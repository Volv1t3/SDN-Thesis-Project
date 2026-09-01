package org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state;
import com.google.common.base.MoreObjects;
import java.lang.Class;
import java.lang.NullPointerException;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.util.NoSuchElementException;
import java.util.Objects;
import javax.annotation.processing.Generated;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.ControllerState;
import org.opendaylight.yang.svc.v1.urn.sma.sdn.csa.rev260831.YangModuleInfoImpl;
import org.opendaylight.yangtools.binding.ChildOf;
import org.opendaylight.yangtools.binding.EntryObject;
import org.opendaylight.yangtools.binding.lib.CodeHelpers;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint64;

/**
 *
 * <p>
 * This class represents the following YANG schema fragment defined in module <b>csa</b>
 * <pre>
 * list bgp-ls-node {
 *   key router-id;
 *   leaf router-id {
 *     type string;
 *   }
 *   leaf topology-id {
 *     type string;
 *   }
 *   leaf node-id {
 *     type string;
 *   }
 *   leaf te-router-id-ipv4 {
 *     type string;
 *   }
 *   leaf graph-node-id {
 *     type uint64;
 *   }
 * }
 * </pre>
 * <p>To create instances of this class use {@link BgpLsNodeBuilder}.
 * @see BgpLsNodeBuilder
 * @see BgpLsNodeKey
 *
 */
@Generated("mdsal-binding-generator")
public interface BgpLsNode
    extends
    ChildOf<ControllerState>,
    EntryObject<BgpLsNode, BgpLsNodeKey>
{



    /**
     * YANG identifier of the statement represented by this class.
     */
    public static final @NonNull QName QNAME = YangModuleInfoImpl.qnameOf("bgp-ls-node");

    @Override
    default Class<org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.BgpLsNode> implementedInterface() {
        return org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.BgpLsNode.class;
    }
    
    /**
     * Default implementation of {@link Object#hashCode()} contract for this interface.
     * Implementations of this interface are encouraged to defer to this method to get consistent hashing
     * results across all implementations.
     *
     * @param obj Object for which to generate hashCode() result.
     * @return Hash code value of data modeled by this interface.
     * @throws NullPointerException if {@code obj} is {@code null}
     */
    static int bindingHashCode(final org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.@NonNull BgpLsNode obj) {
        int result = 1;
        final int prime = 31;
        result = prime * result + Objects.hashCode(obj.getGraphNodeId());
        result = prime * result + Objects.hashCode(obj.getNodeId());
        result = prime * result + Objects.hashCode(obj.getRouterId());
        result = prime * result + Objects.hashCode(obj.getTeRouterIdIpv4());
        result = prime * result + Objects.hashCode(obj.getTopologyId());
        for (var augmentation : obj.augmentations().values()) {
            result += augmentation.hashCode();
        }
        return result;
    }
    
    /**
     * Default implementation of {@link Object#equals(Object)} contract for this interface.
     * Implementations of this interface are encouraged to defer to this method to get consistent equality
     * results across all implementations.
     *
     * @param thisObj Object acting as the receiver of equals invocation
     * @param obj Object acting as argument to equals invocation
     * @return True if thisObj and obj are considered equal
     * @throws NullPointerException if {@code thisObj} is {@code null}
     */
    static boolean bindingEquals(final org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.@NonNull BgpLsNode thisObj, final Object obj) {
        if (thisObj == obj) {
            return true;
        }
        final var other = CodeHelpers.checkCast(org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.BgpLsNode.class, obj);
        return other != null
            && Objects.equals(thisObj.getGraphNodeId(), other.getGraphNodeId())
            && Objects.equals(thisObj.getNodeId(), other.getNodeId())
            && Objects.equals(thisObj.getRouterId(), other.getRouterId())
            && Objects.equals(thisObj.getTeRouterIdIpv4(), other.getTeRouterIdIpv4())
            && Objects.equals(thisObj.getTopologyId(), other.getTopologyId())
            && thisObj.augmentations().equals(other.augmentations());
    }
    
    /**
     * Default implementation of {@link Object#toString()} contract for this interface.
     * Implementations of this interface are encouraged to defer to this method to get consistent string
     * representations across all implementations.
     *
     * @param obj Object for which to generate toString() result.
     * @return {@link String} value of data modeled by this interface.
     * @throws NullPointerException if {@code obj} is {@code null}
     */
    static String bindingToString(final org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.@NonNull BgpLsNode obj) {
        final var helper = MoreObjects.toStringHelper("BgpLsNode");
        CodeHelpers.appendValue(helper, "graphNodeId", obj.getGraphNodeId());
        CodeHelpers.appendValue(helper, "nodeId", obj.getNodeId());
        CodeHelpers.appendValue(helper, "routerId", obj.getRouterId());
        CodeHelpers.appendValue(helper, "teRouterIdIpv4", obj.getTeRouterIdIpv4());
        CodeHelpers.appendValue(helper, "topologyId", obj.getTopologyId());
        CodeHelpers.appendAugmentations(helper, "augmentation", obj);
        return helper.toString();
    }
    
    @Override
    BgpLsNodeKey key();
    
    /**
     * Return routerId, or {@code null} if it is not present.
     *
     * @return {@code String} routerId, or {@code null} if it is not present.
     *
     */
    String getRouterId();
    
    /**
     * Return routerId, guaranteed to be non-null.
     *
     * @return {@code String} routerId, guaranteed to be non-null.
     * @throws NoSuchElementException if routerId is not present
     *
     */
    default @NonNull String requireRouterId() {
        return CodeHelpers.require(getRouterId(), "routerid");
    }
    
    /**
     * Return topologyId, or {@code null} if it is not present.
     *
     * @return {@code String} topologyId, or {@code null} if it is not present.
     *
     */
    String getTopologyId();
    
    /**
     * Return topologyId, guaranteed to be non-null.
     *
     * @return {@code String} topologyId, guaranteed to be non-null.
     * @throws NoSuchElementException if topologyId is not present
     *
     */
    default @NonNull String requireTopologyId() {
        return CodeHelpers.require(getTopologyId(), "topologyid");
    }
    
    /**
     * Return nodeId, or {@code null} if it is not present.
     *
     * @return {@code String} nodeId, or {@code null} if it is not present.
     *
     */
    String getNodeId();
    
    /**
     * Return nodeId, guaranteed to be non-null.
     *
     * @return {@code String} nodeId, guaranteed to be non-null.
     * @throws NoSuchElementException if nodeId is not present
     *
     */
    default @NonNull String requireNodeId() {
        return CodeHelpers.require(getNodeId(), "nodeid");
    }
    
    /**
     * Return teRouterIdIpv4, or {@code null} if it is not present.
     *
     * @return {@code String} teRouterIdIpv4, or {@code null} if it is not present.
     *
     */
    String getTeRouterIdIpv4();
    
    /**
     * Return teRouterIdIpv4, guaranteed to be non-null.
     *
     * @return {@code String} teRouterIdIpv4, guaranteed to be non-null.
     * @throws NoSuchElementException if teRouterIdIpv4 is not present
     *
     */
    default @NonNull String requireTeRouterIdIpv4() {
        return CodeHelpers.require(getTeRouterIdIpv4(), "terouteridipv4");
    }
    
    /**
     * Return graphNodeId, or {@code null} if it is not present.
     *
     * @return {@code Uint64} graphNodeId, or {@code null} if it is not present.
     *
     */
    Uint64 getGraphNodeId();
    
    /**
     * Return graphNodeId, guaranteed to be non-null.
     *
     * @return {@code Uint64} graphNodeId, guaranteed to be non-null.
     * @throws NoSuchElementException if graphNodeId is not present
     *
     */
    default @NonNull Uint64 requireGraphNodeId() {
        return CodeHelpers.require(getGraphNodeId(), "graphnodeid");
    }

}

