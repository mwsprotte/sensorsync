package com.mws.sensorsync;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class ScreenController {
    @RequestMapping(method = RequestMethod.GET, value = "/")
    public String homePage() {
        return "index.html";
    }

}
