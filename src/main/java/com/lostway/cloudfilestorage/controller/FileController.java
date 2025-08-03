package com.lostway.cloudfilestorage.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class FileController {

    @GetMapping("/start")
    public String index() {
        return "Hello World";
    }
}
