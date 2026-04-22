android {
    namespace = "com.android.system.daemon"
    compileSdk = 34

    defaultConfig {
        //...
    }

    buildTypes {
        //...
    }

    // 下面这段是新加的
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
