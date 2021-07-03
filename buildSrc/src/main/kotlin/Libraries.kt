@file:Suppress("MayBeConstant", "SpellCheckingInspection")

// https://mvnrepository.com/artifact/org.eclipse.jetty/jetty-server
// DO NOT UPGRADE UNTIL JETTY USES A STABLE VERSION OF SLF4J
private const val jettyVersion = "9.4.42.v20210604"

// https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter-api
private const val junitVersion = "5.7.2"

object Libraries {
    // https://mvnrepository.com/artifact/net.sourceforge.argparse4j/argparse4j
    val argparse4j: String = "net.sourceforge.argparse4j:argparse4j:0.9.0"

    // https://mvnrepository.com/artifact/org.zeromq/jeromq
    val jeromq: String = "org.zeromq:jeromq:0.5.2"

    val jetty_server: String = "org.eclipse.jetty:jetty-server:$jettyVersion"
    val junit_jupiter_api: String = "org.junit.jupiter:junit-jupiter-api:$junitVersion"
    val junit_jupiter_engine: String = "org.junit.jupiter:junit-jupiter-engine:$junitVersion"

    // https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
    val logback_classic: String = "ch.qos.logback:logback-classic:1.2.3"

    // https://mvnrepository.com/artifact/net.logstash.logback/logstash-logback-encoder
    val logstash_logback_encoder: String = "net.logstash.logback:logstash-logback-encoder:6.6"

    // https://mvnrepository.com/artifact/org.mongodb/mongodb-driver
    val mongodb_driver: String = "org.mongodb:mongodb-driver:3.12.8"

    // https://mvnrepository.com/artifact/com.grack/nanojson
    val nanojson: String = "com.grack:nanojson:1.7"

    // https://ooverkommelig.org/
    val ooverkommelig: String = "org.ooverkommelig:ooverkommelig-jvm:1beta3"

    // https://mvnrepository.com/artifact/org.slf4j/slf4j-api
    val slf4j_api: String = "org.slf4j:slf4j-api:1.7.31"
}
