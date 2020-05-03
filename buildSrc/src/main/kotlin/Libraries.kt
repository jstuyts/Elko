@file:Suppress("MayBeConstant", "SpellCheckingInspection")

private const val jettyVersion = "9.4.28.v20200408"
private const val junitVersion = "5.6.1"

object Libraries {
    val argparse4j = "net.sourceforge.argparse4j:argparse4j:0.8.1"
    val jeromq = "org.zeromq:jeromq:0.5.1"
    val jetty_server = "org.eclipse.jetty:jetty-server:$jettyVersion"
    val junit_jupiter_api = "org.junit.jupiter:junit-jupiter-api:$junitVersion"
    val junit_jupiter_engine = "org.junit.jupiter:junit-jupiter-engine:$junitVersion"
    val logback_classic = "ch.qos.logback:logback-classic:1.2.3"
    val logstash_logback_encoder = "net.logstash.logback:logstash-logback-encoder:6.3"
    val mongodb_driver = "org.mongodb:mongodb-driver:3.4.3"
    val nanojson = "com.grack:nanojson:1.6"
    val ooverkommelig = "org.ooverkommelig:ooverkommelig-jvm8:1beta1"
    val slf4j_api = "org.slf4j:slf4j-api:1.7.30"
}
