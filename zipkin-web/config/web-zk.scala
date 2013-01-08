import com.twitter.zipkin.builder.{WebBuilder, ZooKeeperClientBuilder, QueryClient}

val zkClientBuilder = ZooKeeperClientBuilder(Seq("ZOOKEEPER_HOSTS"))
val queryClient = QueryClient.zookeeper(zkClientBuilder, "/path/to/server/set")
WebBuilder("http://localhost:8080/", queryClient)
