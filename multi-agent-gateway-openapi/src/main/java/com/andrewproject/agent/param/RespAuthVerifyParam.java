package com.andrewproject.agent.param;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class RespAuthVerifyParam {

    private Boolean verifyResult;

    private String failMsg;
}
