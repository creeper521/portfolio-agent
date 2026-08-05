/* ════════════════════════════════════════════════════════════════════
   资产包组合推荐 · 原型共享逻辑（_selection-demo-core.js）

   纯 vanilla JS，无依赖，file:// 可直接运行。
   三个入口原型共用：表单 + 结果 + 状态控制条。

   契约纪律（与 Vue 实现保持一致）：
     · 只渲染响应字段；subjectId / claimId / evidenceId 仅内存映射，不进 DOM
     · 资产序号 01/02/03 是前端展示映射，不是后端 ID
     · coverage[].label 后端原样返回 code，中文来自前端 capabilityLabels 映射
     · 访客输入仅内存态：不写 URL、不写 history、不写 localStorage
   ════════════════════════════════════════════════════════════════════ */
(function () {
  'use strict';

  var D = window.SELECTION_DEMO;

  /* ── 展示文案映射（前端展示决策，不改后端枚举） ── */
  var STATUS_LABEL = {
    READY: '组合已生成',
    INSUFFICIENT: '覆盖不充分',
    TEMPORARILY_UNAVAILABLE: '暂时不可用',
  };
  var TYPE_LABEL = { PROJECT: 'PROJECT', CASE: 'CASE' };

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }
  function capLabel(code) {
    var zh = D.capabilityLabels[code];
    return zh ? zh + ' ' + code : code;
  }
  /* 把后端机械句式里的能力码翻成「中文 CODE」并排展示 */
  function humanizeReason(text) {
    return esc(text).replace(/[A-Z][A-Z_]+/g, function (m) {
      return D.capabilityLabels[m] ? D.capabilityLabels[m] + ' ' + m : m;
    });
  }

  /* ════════════════════════════════════════════════════════════════
     表单
     ════════════════════════════════════════════════════════════════ */
  function renderForm(state) {
    var roles = D.roles.map(function (r) {
      return '<button type="button" class="sel-role" data-role="' + r.code + '" aria-pressed="' + (state.audienceRole === r.code) + '">' +
        '<span class="sel-role__name">' + esc(r.label) + '</span>' +
        '<span class="sel-role__desc">' + esc(r.desc) + '</span></button>';
    }).join('');

    var tracks = D.careerTracks.map(function (t) {
      return '<button type="button" class="sel-seg__btn" data-track="' + esc(t.code) + '" aria-pressed="' + (state.careerTrack === t.code) + '">' + esc(t.label) + '</button>';
    }).join('');

    var caps = D.capabilityOptions.map(function (c) {
      var on = state.capabilityCodes.indexOf(c) !== -1;
      return '<button type="button" class="sel-chip" data-cap="' + c + '" aria-pressed="' + on + '">' +
        esc(D.capabilityLabels[c] || c) + '<span class="sel-chip__code">' + c + '</span></button>';
    }).join('');

    var sizes = [2, 3, 4, 5].map(function (n) {
      return '<button type="button" class="sel-seg__btn" data-size="' + n + '" aria-pressed="' + (state.requestedSize === n) + '">0' + n + '</button>';
    }).join('');

    return '' +
      '<div class="sel-form__code">SELECTION / 生成条件</div>' +

      '<div class="sel-field">' +
        '<div class="sel-field__label">求职方向<small>影响候选资产的方向过滤</small></div>' +
        '<div class="sel-field__control"><div class="sel-seg" role="group" aria-label="求职方向">' + tracks + '</div></div>' +
      '</div>' +

      '<div class="sel-field" id="selRoleField">' +
        '<div class="sel-field__label">你的身份<small>必填 · 影响推荐理由的表达口径</small></div>' +
        '<div class="sel-field__control"><div class="sel-role-grid" role="group" aria-label="访客身份">' + roles + '</div></div>' +
      '</div>' +

      '<div class="sel-field">' +
        '<div class="sel-field__label">能力目标<small>可多选 · 不选则按资产实际能力汇总</small></div>' +
        '<div class="sel-field__control"><div class="sel-chips" role="group" aria-label="能力目标">' + caps + '</div></div>' +
      '</div>' +

      '<div class="sel-field">' +
        '<div class="sel-field__label">资产数量<small>2–5 项，默认 3</small></div>' +
        '<div class="sel-field__control"><div class="sel-seg" role="group" aria-label="资产数量">' + sizes + '</div></div>' +
      '</div>' +

      '<div class="sel-field sel-field--optional">' +
        '<div class="sel-field__label">自然语言目标<small>可选</small></div>' +
        '<div class="sel-field__control">' +
          '<div class="sel-goal"><input type="text" id="selGoalInput" maxlength="120" placeholder="例：想看能证明故障排查与交付闭环的组合" aria-label="自然语言目标（可选）"></div>' +
          '<p class="sel-goal__note">输入仅用于本次生成：不保存、不写入浏览器历史，刷新或关闭页面即消失。</p>' +
        '</div>' +
      '</div>' +

      '<div class="sel-submit-row">' +
        '<button type="button" class="sel-submit" id="selSubmit">生成资产包</button>' +
        '<span class="sel-form__hint">基于已审核公开资产生成，推荐理由附公开证据。</span>' +
      '</div>' +
      '<div id="selFormError" aria-live="polite"></div>';
  }

  function bindForm(formEl, state, onSubmit) {
    formEl.addEventListener('click', function (e) {
      var t = e.target.closest('button');
      if (!t) return;
      if (t.dataset.role) {
        state.audienceRole = state.audienceRole === t.dataset.role ? null : t.dataset.role;
        formEl.querySelectorAll('[data-role]').forEach(function (b) {
          b.setAttribute('aria-pressed', b.dataset.role === state.audienceRole);
        });
        clearError(formEl);
      } else if (t.hasAttribute('data-track')) {
        state.careerTrack = t.dataset.track;
        formEl.querySelectorAll('[data-track]').forEach(function (b) {
          b.setAttribute('aria-pressed', b.dataset.track === state.careerTrack);
        });
      } else if (t.dataset.cap) {
        var i = state.capabilityCodes.indexOf(t.dataset.cap);
        if (i === -1) state.capabilityCodes.push(t.dataset.cap); else state.capabilityCodes.splice(i, 1);
        t.setAttribute('aria-pressed', i === -1);
      } else if (t.dataset.size) {
        state.requestedSize = parseInt(t.dataset.size, 10);
        formEl.querySelectorAll('[data-size]').forEach(function (b) {
          b.setAttribute('aria-pressed', parseInt(b.dataset.size, 10) === state.requestedSize);
        });
      } else if (t.id === 'selSubmit') {
        if (!state.audienceRole) {
          showError(formEl, '请先选择你的身份——它决定推荐理由的表达口径。其余条件均可留空。');
          return;
        }
        state.goal = (formEl.querySelector('#selGoalInput') || {}).value || '';
        onSubmit();
      }
    });
  }

  function showError(formEl, msg) {
    var box = formEl.querySelector('#selFormError');
    box.innerHTML = '<p class="sel-form-error" role="alert" tabindex="-1">' + esc(msg) + '</p>';
    var field = formEl.querySelector('#selRoleField');
    if (field) field.style.borderBottomColor = 'var(--red)';
    box.querySelector('.sel-form-error').focus();
  }
  function clearError(formEl) {
    var box = formEl.querySelector('#selFormError');
    if (box) box.innerHTML = '';
    var field = formEl.querySelector('#selRoleField');
    if (field) field.style.borderBottomColor = '';
  }

  /* ════════════════════════════════════════════════════════════════
     结果区
     ════════════════════════════════════════════════════════════════ */
  function renderPlaceholder() {
    return '<div class="sel-empty" style="padding:52px 0 44px">' +
      '<div class="sel-empty__code">SELECTION / 待生成</div>' +
      '<h3 class="sel-empty__title">填好上面的条件，生成一套互补的资产组合。</h3>' +
      '<p class="sel-empty__desc">结果不是搜索结果列表：你会看到这套组合覆盖了哪些能力、资产之间如何互补，以及每条理由对应的公开证据。</p>' +
      '</div>';
  }

  function renderLoading() {
    var card = '<div class="sel-skel-card">' +
      '<div class="sel-skel-bar sel-skel-bar--w30"></div>' +
      '<div class="sel-skel-bar sel-skel-bar--w80"></div>' +
      '<div class="sel-skel-bar sel-skel-bar--w55"></div>' +
      '</div>';
    return '<div class="sel-loading" aria-busy="true">' +
      '<div class="sel-loading__code">SELECTION / 生成中…</div>' +
      '<div class="sel-skel-grid">' + card + card + card + '</div>' +
      '</div>';
  }

  /* subjectId → 01/02/03 序号（内存映射，ID 不进 DOM） */
  function indexMap(items) {
    var m = {};
    items.forEach(function (it, i) { m[it.subjectId] = String(i + 1).padStart(2, '0'); });
    return m;
  }

  function renderNote(res) {
    if (!res.degradation) return '';
    var alert = res.status !== 'READY';
    return '<div class="sel-note' + (alert ? ' sel-note--alert' : '') + '" role="status">' +
      '<span class="sel-note__code">' + esc(res.degradation.code) + '</span>' +
      '<span>' + esc(res.degradation.message) + '</span>' +
      (res.status === 'INSUFFICIENT'
        ? '<span>实际入选 ' + res.actualSize + ' / 请求 ' + res.requestedSize + ' 项——不凑数、不用弱证据补齐。</span>'
        : '') +
      '</div>';
  }

  function renderAsset(it, no) {
    var caps = it.capabilities.slice().sort().map(function (c) {
      return '<span class="sel-cap">' + esc(D.capabilityLabels[c] || c) + '<span class="sel-cap__code">' + c + '</span></span>';
    }).join('');
    var evi = it.evidenceRefs.map(function (r) {
      return '<span class="sel-evi">' + esc(r.label) + '</span>';
    }).join('');
    return '<article class="sel-asset">' +
      '<div class="sel-asset__top"><span class="sel-asset__no">' + no + '</span>' +
        '<span class="type-tag" data-t="' + esc(it.subjectType) + '">' + esc(TYPE_LABEL[it.subjectType] || it.subjectType) + '</span></div>' +
      '<h3 class="sel-asset__title">' + esc(it.title) + '</h3>' +
      '<p class="sel-asset__summary">' + esc(it.summary) + '</p>' +
      '<div class="sel-asset__caps">' + caps + '</div>' +
      '<p class="sel-asset__reason"><b>入选理由</b> · ' + humanizeReason(it.selectionReason) + '</p>' +
      '<div class="sel-asset__evidence">' + evi + '</div>' +
      '<a class="sel-asset__link" href="' + esc(it.route) + '" data-proto-link="' + esc(it.route) + '">查看公开页 ↗</a>' +
      '</article>';
  }

  function renderMatrix(res, idx) {
    if (!res.coverage.length) return '';
    var head = res.items.map(function (it) { return '<th scope="col">' + idx[it.subjectId] + '</th>'; }).join('');
    var rows = res.coverage.map(function (c) {
      var missing = !c.coveredBySubjectIds.length;
      var cells = res.items.map(function (it) {
        var on = c.coveredBySubjectIds.indexOf(it.subjectId) !== -1;
        return '<td><span class="sel-dot ' + (on ? 'sel-dot--on' : 'sel-dot--off') + '"' +
          ' aria-label="' + (on ? '覆盖' : '未覆盖') + '"></span></td>';
      }).join('');
      return '<tr' + (missing ? ' class="is-missing"' : '') + '>' +
        '<th scope="row"><span class="sel-matrix__cap">' +
          '<span class="sel-matrix__cap-label">' + esc(D.capabilityLabels[c.capabilityCode] || c.capabilityCode) + '</span>' +
          '<span class="sel-matrix__cap-code">' + esc(c.capabilityCode) + '</span></span></th>' +
        cells +
        '<td>' + (missing ? '<span class="sel-matrix__missing">未覆盖</span>' : '') + '</td>' +
        '</tr>';
    }).join('');
    return '<table class="sel-matrix"><thead><tr><th scope="col">能力</th>' + head + '<th scope="col"></th></tr></thead>' +
      '<tbody>' + rows + '</tbody></table>';
  }

  function renderPairs(res, idx) {
    if (!res.complementarity.length) return '';
    var rows = res.complementarity.map(function (p) {
      return '<div class="sel-pair">' +
        '<span class="sel-pair__ids">' + (idx[p.leftSubjectId] || '?') + ' ↔ ' + (idx[p.rightSubjectId] || '?') + '</span>' +
        '<span class="sel-pair__reason">' + humanizeReason(p.reason) + '</span></div>';
    }).join('');
    return '<div class="sel-pairs">' + rows + '</div>';
  }

  function renderAlts(res) {
    if (!res.alternatives.length) return '';
    var rows = res.alternatives.map(function (a) {
      return '<a class="sel-alt" href="' + esc(a.route) + '" data-proto-link="' + esc(a.route) + '">' +
        '<span class="type-tag" data-t="' + esc(a.subjectType) + '">' + esc(TYPE_LABEL[a.subjectType] || a.subjectType) + '</span>' +
        '<span><span class="sel-alt__title">' + esc(a.title) + '</span>' +
        '<span class="sel-alt__reason">' + esc(a.reason) + '</span></span>' +
        '<span class="sel-alt__go">查看 ↗</span></a>';
    }).join('');
    return '<div class="sel-alts">' + rows + '</div>';
  }

  function renderDebug(res) {
    return '<details class="sel-debug"><summary>推荐依据与版本</summary><dl>' +
      '<div><dt>RELEASE</dt><dd>' + esc(res.releaseVersion) + '</dd></div>' +
      '<div><dt>POLICY</dt><dd>' + esc(res.policyVersion) + '</dd></div>' +
      '<div><dt>RETRIEVAL</dt><dd>' + esc(res.retrievalMode) + '</dd></div>' +
      '<div><dt>SELECTION</dt><dd>' + esc(res.selectionMode) + '</dd></div>' +
      '</dl></details>';
  }

  function renderEmpty(kind) {
    if (kind === 'notEnabled') {
      return '<div class="sel-empty">' +
        '<div class="sel-empty__code">SELECTION / 未启用</div>' +
        '<h3 class="sel-empty__title">当前部署未开启组合推荐。</h3>' +
        '<p class="sel-empty__desc">这不影响任何已有内容——全部项目与案例仍可正常浏览。</p>' +
        '<div class="sel-empty__links"><a href="#/projects" data-proto-link="/projects">浏览项目</a><a href="#/cases" data-proto-link="/cases">浏览案例</a></div>' +
        '</div>';
    }
    if (kind === 'unavailable') {
      return '<div class="sel-empty">' +
        '<div class="sel-empty__code">TEMPORARILY UNAVAILABLE</div>' +
        '<h3 class="sel-empty__title">组合推荐暂时不可用。</h3>' +
        '<p class="sel-empty__desc">不会展示未经验证的推荐。现有作品浏览不受任何影响，你可以直接从目录进入。</p>' +
        '<div class="sel-empty__links"><a href="#/projects" data-proto-link="/projects">浏览项目</a><a href="#/cases" data-proto-link="/cases">浏览案例</a></div>' +
        '</div>';
    }
    return '<div class="sel-empty">' +
      '<div class="sel-empty__code">EMPTY RESULT</div>' +
      '<h3 class="sel-empty__title">没有找到符合条件的公开资产。</h3>' +
      '<p class="sel-empty__desc">可以放宽能力目标或求职方向再试一次；或直接浏览全部公开作品。</p>' +
      '<div class="sel-empty__links"><a href="#/projects" data-proto-link="/projects">浏览项目</a><a href="#/cases" data-proto-link="/cases">浏览案例</a></div>' +
      '</div>';
  }

  function renderResult(res) {
    if (res.status === 'TEMPORARILY_UNAVAILABLE') return renderEmpty('unavailable');
    if (!res.items.length) return renderEmpty('empty');

    var idx = indexMap(res.items);
    var covered = res.coverage.filter(function (c) { return c.coveredBySubjectIds.length; }).length;
    var total = res.coverage.length;
    var n = res.items.length;

    var head = '<div class="sel-result__head">' +
      '<span class="sel-result__code">SELECTION / ' + esc(STATUS_LABEL[res.status] || res.status) + '</span>' +
      '<span class="sel-result__meta">' + res.actualSize + ' / ' + res.requestedSize + ' 项资产</span>' +
      '</div>' +
      '<div class="sel-result__lead">' +
        '<h2 class="sel-result__title">一套 ' + res.actualSize + ' 项互补资产</h2>' +
        '<span class="sel-result__count">能力覆盖 ' + covered + ' / ' + total + '</span>' +
      '</div>' +
      '<p class="sel-result__summary">编号即阅读顺序。每项资产标注了它覆盖的能力与公开证据；下方矩阵给出整套组合的能力分布，互补关系说明为什么这几项放在一起比单看任何一项都更完整。</p>';

    var bundle = '<div class="sel-bundle sel-bundle--n' + n + '" role="list">' +
      res.items.map(function (it, i) {
        return renderAsset(it, idx[it.subjectId]).replace('<article class="sel-asset"', '<article role="listitem" class="sel-asset"');
      }).join('') + '</div>';

    var matrix = '<div class="sel-panel">' +
      '<div class="sel-panel__head"><h3 class="sel-panel__title">能力覆盖矩阵</h3>' +
      '<span class="sel-panel__note">行 = 能力 · 列 = 资产编号</span></div>' +
      '<div class="sel-matrix-wrap">' + renderMatrix(res, idx) + '</div></div>';

    var pairs = res.complementarity.length
      ? '<div class="sel-panel"><div class="sel-panel__head"><h3 class="sel-panel__title">资产如何互补</h3>' +
        '<span class="sel-panel__note">' + res.complementarity.length + ' 组关系</span></div>' + renderPairs(res, idx) + '</div>'
      : '';

    var alts = res.alternatives.length
      ? '<div class="sel-panel"><div class="sel-panel__head"><h3 class="sel-panel__title">替代候选</h3>' +
        '<span class="sel-panel__note">候选有效，但未入选当前组合</span></div>' + renderAlts(res) + '</div>'
      : '';

    return '<div class="sel-result reveal">' + head + renderNote(res) + bundle + matrix + pairs + alts + renderDebug(res) + '</div>';
  }

  /* ════════════════════════════════════════════════════════════════
     控制条 + 挂载
     ════════════════════════════════════════════════════════════════ */
  var RAIL_STATES = [
    { key: 'ready',        label: 'READY' },
    { key: 'insufficient', label: 'INSUFFICIENT' },
    { key: 'ftsOnly',      label: 'FTS-ONLY 降级' },
    { key: 'unavailable',  label: '暂不可用' },
    { key: 'notEnabled',   label: '未启用 404' },
    { key: 'empty',        label: '空结果' },
  ];

  function mount(opts) {
    var formEl = opts.form, resultEl = opts.result, railEl = opts.rail;
    var state = { careerTrack: '', audienceRole: null, capabilityCodes: [], requestedSize: 3, goal: '' };
    var currentKey = null;
    var timer = null;

    function paint(key) {
      currentKey = key;
      if (railEl) {
        railEl.querySelectorAll('button[data-state]').forEach(function (b) {
          b.classList.toggle('is-on', b.dataset.state === key);
        });
      }
      resultEl.innerHTML = key === 'notEnabled' ? renderEmpty('notEnabled') : renderResult(D.responses[key]);
      resultEl.removeAttribute('aria-busy');
      var focusTarget = resultEl.querySelector('.sel-result__code, .sel-empty__code');
      if (focusTarget) { focusTarget.setAttribute('tabindex', '-1'); focusTarget.focus({ preventScroll: true }); }
    }

    function paintWithSkeleton(key, delay) {
      if (timer) clearTimeout(timer);
      resultEl.innerHTML = renderLoading();
      resultEl.setAttribute('aria-busy', 'true');
      timer = setTimeout(function () { paint(key); }, delay);
    }

    if (formEl) {
      formEl.innerHTML = renderForm(state);
      bindForm(formEl, state, function () {
        paintWithSkeleton(currentKey || 'ready', 600);
      });
    }

    resultEl.innerHTML = renderPlaceholder();

    /* 原型里所有资产链接不跳转，只提示真实路由 */
    resultEl.addEventListener('click', function (e) {
      var a = e.target.closest('[data-proto-link]');
      if (a) { e.preventDefault(); }
    });

    if (railEl) {
      railEl.innerHTML = '<span class="demo-rail__tag">PROTOTYPE · 状态切换</span>' +
        RAIL_STATES.map(function (s) {
          return '<button type="button" data-state="' + s.key + '">' + s.label + '</button>';
        }).join('');
      railEl.addEventListener('click', function (e) {
        var b = e.target.closest('button[data-state]');
        if (!b) return;
        if (b.dataset.state === 'notEnabled') { paint('notEnabled'); return; }
        paintWithSkeleton(b.dataset.state, 350);
      });
    }

    return { paint: paint, state: state };
  }

  window.SelectionDemo = { mount: mount };
})();
