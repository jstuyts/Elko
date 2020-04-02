rootProject.name = "Elko"

include("Actor")
include("Boot:Api")
include("Boot:App")
include("ServerManagement")
include("ByteIoFramer:Api")
include("ByteIoFramer:Http")
include("ByteIoFramer:Json")
include("ByteIoFramer:Rtcp")
include("ByteIoFramer:WebSocket")
include("Communication")
include("Example")
include("FileObjectStore")
include("Json")
include("JsonMessageHandling")
include("JsonValidator")
include("LogEater")
include("MongoObjectStore")
include("Net:Api")
include("Net:Http")
include("Net:Rtcp")
include("Net:Tcp")
include("Net:Zeromq")
include("ObjectDatabase:Api")
include("ObjectDatabase:Local")
include("ObjectDatabase:Remote")
include("Properties")
include("Run:BasicCluster")
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
include("Run:WorkshopDev")
include("Running")
include("ScalableSsl")
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
