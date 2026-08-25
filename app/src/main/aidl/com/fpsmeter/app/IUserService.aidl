package com.fpsmeter.app;

interface IUserService {
    String execCommand(String cmd) = 1;
    void destroy() = 16777114;
}
