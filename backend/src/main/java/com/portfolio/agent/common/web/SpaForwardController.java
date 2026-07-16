package com.portfolio.agent.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({"/projects/{slug}", "/projects/{slug}/"})
    public String forwardProjectRoute() {
        return "forward:/index.html";
    }
}
