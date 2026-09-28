package com.andrewproject.agent.service;

import com.andrewproject.agent.param.ReqAuthVerifyParam;
import com.andrewproject.agent.param.RespAuthVerifyParam;

public interface AuthService {

    RespAuthVerifyParam verifyAuth(ReqAuthVerifyParam reqAuthVerifyParam);
}
