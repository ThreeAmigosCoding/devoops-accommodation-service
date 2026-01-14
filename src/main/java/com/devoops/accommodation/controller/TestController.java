package com.devoops.accommodation.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/accommodation")
public class TestController {

    @GetMapping("test")
    public String test() {
        return "Accommodation Service is up and running!";
    }
}
