package com.example.invoice_service.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ReactController {

    @RequestMapping(value = {
            "/",
            "/invoice/**",
            "/history/**",
            "/order/**"
    },  produces = "text/html")
    public String forward() {
        return "forward:/index.html";
    }
}