package com.mws.sensorsync;

import com.mws.sensorsync.services.ProjectMetadataServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projectmetadata")
public class ProjectMetadataController {

    @Autowired
    private ProjectMetadataServices services;




}
