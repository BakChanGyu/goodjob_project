package com.goodjob.api.controller.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodjob.common.error.ErrorCode;
import com.goodjob.common.error.exception.BusinessException;
import com.goodjob.resume.dto.request.ResumeRequest;


import java.beans.PropertyEditorSupport;

public class ResumeRequestEditor extends PropertyEditorSupport {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void setAsText(String input) {
        try {
            ResumeRequest resumeRequest = objectMapper.readValue(input, ResumeRequest.class);
            setValue(resumeRequest);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.JSON_PARSING_FAILED);
        }
    }
}
