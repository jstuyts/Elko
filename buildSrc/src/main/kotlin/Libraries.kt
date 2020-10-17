@file:Suppress("MayBeConstant", "SpellCheckingInspection")

private const val jettyVersion = "9.4.32.v20200930"
private const val junitVersion = "5.7.0"

object Libraries {
    val argparse4j: String = "net.sourceforge.argparse4j:argparse4j:0.8.1"
    val jeromq: String = "org.zeromq:jeromq:0.5.2"
    val jetty_server: String = "org.eclipse.jetty:jetty-server:$jettyVersion"
    val junit_jupiter_api: String = "org.junit.jupiter:junit-jupiter-api:$junitVersion"
    val junit_jupiter_engine: String = "org.junit.jupiter:junit-jupiter-engine:$junitVersion"
    val logback_classic: String = "ch.qos.logback:logback-classic:1.2.3"
    val logstash_logback_encoder: String = "net.logstash.logback:logstash-logback-encoder:6.4"
    val mongodb_driver: String = "org.mongodb:mongodb-driver:3.4.3"
    val nanojson: String = "com.grack:nanojson:1.6"
    val ooverkommelig: String = "org.ooverkommelig:ooverkommelig-jvm8:1beta1"
    val slf4j_api: String = "org.slf4j:slf4j-api:1.7.30"
}
