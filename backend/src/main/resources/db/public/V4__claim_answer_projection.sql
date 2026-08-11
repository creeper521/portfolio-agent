-- 为已发布的公共 Claim 保存 Composer 所需的完整语义。
-- 历史行保持 NULL：查询层不会把它们伪装成完整 Claim，
-- 新 Release 由 Importer 写入全部字段。
ALTER TABLE claim
    ADD COLUMN detail text,
    ADD COLUMN achievement_status varchar(40),
    ADD COLUMN contribution_type varchar(40),
    ADD COLUMN verification_basis varchar(40),
    ADD COLUMN materiality varchar(40),
    ADD COLUMN topics jsonb;
