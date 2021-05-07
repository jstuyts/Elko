rootProject.name = "Elko"

include("Actor")
include("Boot:Api")
include("Boot:App")
include("ServerManagement")
include("ByteIoFramer:Api")
include("ByteIoFramer:Http")
include("ByteIoFramer:Json")
include("ByteIoFramer:Rtcp")
include("ByteIoFramer:Websocket")
include("Communication")
include("Example")
include("Feature:BasicExamples")
include("Feature:Capabilities")
include("Feature:DeviceUser")
include("IdGeneration")
include("Json")
include("JsonMessageHandling")
include("MiscTools:JsonValidator")
include("Net:Api")
include("Net:ConnectionRetrier")
include("Net:Http")
include("Net:Rtcp")
include("Net:Tcp")
include("Net:Websocket")
include("Net:Zeromq")
include("Net:ZeromqOutbound")
include("ObjectDatabase:Api")
include("ObjectDatabase:Direct")
include("ObjectDatabase:FileObjectStore")
include("ObjectDatabase:MongoObjectStore")
include("ObjectDatabase:Repository")
include("OrdinalGeneration")
include("Presence:JavaScript")
include("Properties")
include("Run:BasicCluster")
include("Run:ChatBasic")
include("Run:ClusterDev")
include("Run:ClusterManaged")
include("Run:ContextDev")
include("Run:ContextStandalone")
include("Run:BrokerDev")
include("Run:DirectorDev")
include("Run:Example")
include("Run:FullProduction")
include("Run:GatekeeperDev")
include("Run:PresenceDev")
include("Run:RepositoryDev")
include("Run:services:WebServer")
include("Run:WorkshopDev")
include("Running")
include("Server:Broker")
include("Server:Context")
include("Server:Director")
include("Server:Gatekeeper")
include("Server:Presence")
include("Server:Repository")
include("Server:Workshop")
include("ServerCore")
include("ServerMetadata")
include("ServerTest:Context:Test")
include("ServerTest:Context:TestUserFactory")
include("ServerTest:Workshop:Bank")
include("ServerTest:Workshop:Echo")
include("Timer")
include("Trace")
include("Util")
