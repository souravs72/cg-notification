package com.clapgrow.notification.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RootController {
    
    @GetMapping("/")
    public String root() {
        return "redirect:/auth/login";
    }
    
    @GetMapping("/login")
    public String login() {
        return "redirect:/auth/login";
    }
}

