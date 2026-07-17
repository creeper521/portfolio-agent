package com.portfolio.agent.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({
            "/projects",
            "/projects/",
            "/projects/{slug}",
            "/projects/{slug}/",
            "/timeline",
            "/timeline/",
            "/evidence",
            "/evidence/",
            "/agent",
            "/agent/"
    })
    public String forwardPublicRoute() {
        return "forward:/index.html";
    }
}
