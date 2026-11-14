package com.example.gradproj.EduNest.contract;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"prod", "test"})
class MultipartJacksonProdContractTest extends MultipartJacksonParitySupport { }
