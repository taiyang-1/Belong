package com.example.belong;

import org.junit.jupiter.api.Test;

class BelongApplicationTests {

    @Test
    void applicationClassExists() {
        BelongApplication.main(new String[]{"--spring.main.web-application-type=none"});
    }

}
