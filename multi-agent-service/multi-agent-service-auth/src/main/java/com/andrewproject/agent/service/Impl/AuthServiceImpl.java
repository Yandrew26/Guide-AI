package com.andrewproject.agent.service.Impl;

import com.andrewproject.agent.dto.RagAuthBaseDTO;
import com.andrewproject.agent.param.ReqAuthVerifyParam;
import com.andrewproject.agent.param.RespAuthVerifyParam;
import com.andrewproject.agent.service.AuthService;
import com.andrewproject.agent.service.RagAuthBaseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Resource
    private RagAuthBaseService ragAuthBaseService;

    @Override
    public RespAuthVerifyParam verifyAuth(ReqAuthVerifyParam reqAuthVerifyParam) {
        RagAuthBaseDTO ragAuthBaseDTO = ragAuthBaseService.getByAuthId(reqAuthVerifyParam.getAuthId());
        if (ObjectUtils.isEmpty(ragAuthBaseDTO)) {
            return RespAuthVerifyParam.builder().verifyResult(Boolean.FALSE).failMsg("Can not find auth_id").build();
        }

        String authVerifyStr = "authId=" + reqAuthVerifyParam.getAuthId()
                + "&secretKey=" + ragAuthBaseDTO.getSecretKey();
        MessageDigest md5Digest = DigestUtils.getMd5Digest();
        byte[] digest = md5Digest.digest(authVerifyStr.getBytes(StandardCharsets.UTF_8));
        String m5dStr = Hex.encodeHexString(digest);
        // constant-time compare: the signature can't be probed byte by byte via response timing
        String given = reqAuthVerifyParam.getAuthStr() == null ? "" : reqAuthVerifyParam.getAuthStr();
        if (!MessageDigest.isEqual(m5dStr.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8))) {
            return RespAuthVerifyParam.builder().verifyResult(Boolean.FALSE).failMsg("authorization failed").build();
        }
        return RespAuthVerifyParam.builder().verifyResult(Boolean.TRUE).build();
    }

    public static void main(String[] args) {
        String authVerifyStr = "authId=12345&secretKey=54321";
        MessageDigest md5Digest = DigestUtils.getMd5Digest();
        byte[] digest = md5Digest.digest(authVerifyStr.getBytes(StandardCharsets.UTF_8));
        String m5dStr = Hex.encodeHexString(digest);
        System.out.println(m5dStr);
    }
}
