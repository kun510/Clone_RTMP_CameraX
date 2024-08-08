package com.kun510.rtmp.remote.models

data class LoginBody(
    val username:String,
    val password:String
)

data class LoginResponse(
    val token:String
)