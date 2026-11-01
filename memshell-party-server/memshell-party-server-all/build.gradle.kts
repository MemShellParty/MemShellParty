plugins {
    id("java8-library-conventions")
}

description = "Aggregate module that includes all built-in server providers"

dependencies {
    api(project(":memshell-party-server:memshell-party-server-servlet"))
    api(project(":memshell-party-server:memshell-party-server-spring-webmvc"))
    api(project(":memshell-party-server:memshell-party-server-spring-webflux"))
    api(project(":memshell-party-server:memshell-party-server-xxljob"))
    api(project(":memshell-party-server:memshell-party-server-struts2"))
    api(project(":memshell-party-server:memshell-party-server-dubbo"))
}
