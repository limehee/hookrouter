package io.github.limehee.hookrouter.core.port;

import java.util.List;

public interface RoutingPolicy {

    List<RoutingTarget> resolve(String typeId, String category);
}
