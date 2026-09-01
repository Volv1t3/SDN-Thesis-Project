package org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831;
import com.google.common.base.MoreObjects;
import java.lang.Class;
import java.lang.NullPointerException;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import javax.annotation.processing.Generated;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.BgpLsNode;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.BgpLsNodeKey;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.CacheState;
import org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.ControlPlane;
import org.opendaylight.yang.svc.v1.urn.sma.sdn.csa.rev260831.YangModuleInfoImpl;
import org.opendaylight.yangtools.binding.Augmentable;
import org.opendaylight.yangtools.binding.ChildOf;
import org.opendaylight.yangtools.binding.lib.CodeHelpers;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint64;

/**
 * Current controller-side application state for external validation.
 *
 * <p>
 * This class represents the following YANG schema fragment defined in module <b>csa</b>
 * <pre>
 * container controller-state {
 *   config false;
 *   leaf generated-at {
 *     type string;
 *   }
 *   leaf processed-packet-count {
 *     type uint64;
 *   }
 *   container control-plane {
 *     leaf ready {
 *       type boolean;
 *     }
 *     leaf closed {
 *       type boolean;
 *     }
 *     leaf topology-id {
 *       type string;
 *     }
 *     leaf topology-ttl-millis {
 *       type uint64;
 *     }
 *     leaf topology-fresh {
 *       type boolean;
 *     }
 *     leaf topology-refresh-in-progress {
 *       type boolean;
 *     }
 *     leaf topology-last-successful-refresh {
 *       type string;
 *     }
 *     leaf topology-last-refresh-attempt {
 *       type string;
 *     }
 *     leaf topology-fresh-until {
 *       type string;
 *     }
 *     leaf topology-last-failure {
 *       type string;
 *     }
 *     leaf topology-refresh-success-count {
 *       type uint64;
 *     }
 *     leaf topology-refresh-failure-count {
 *       type uint64;
 *     }
 *   }
 *   container cache-state {
 *     leaf classification-entry-count {
 *       type uint32;
 *     }
 *     leaf calculated-path-entry-count {
 *       type uint32;
 *     }
 *     leaf policy-evidence-bucket-count {
 *       type uint32;
 *     }
 *     leaf active-pair-policy-count {
 *       type uint32;
 *     }
 *     leaf delegated-lsp-count {
 *       type uint32;
 *     }
 *     leaf openflow-switch-count {
 *       type uint32;
 *     }
 *   }
 *   list bgp-ls-node {
 *     key router-id;
 *     leaf router-id {
 *       type string;
 *     }
 *     leaf topology-id {
 *       type string;
 *     }
 *     leaf node-id {
 *       type string;
 *     }
 *     leaf te-router-id-ipv4 {
 *       type string;
 *     }
 *     leaf graph-node-id {
 *       type uint64;
 *     }
 *   }
 * }
 * </pre>
 * <p>To create instances of this class use {@link ControllerStateBuilder}.
 * @see ControllerStateBuilder
 *
 */
@Generated("mdsal-binding-generator")
public interface ControllerState
    extends
    ChildOf<CsaData>,
    Augmentable<ControllerState>
{



    /**
     * YANG identifier of the statement represented by this class.
     */
    public static final @NonNull QName QNAME = YangModuleInfoImpl.qnameOf("controller-state");

    @Override
    default Class<org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.ControllerState> implementedInterface() {
        return org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.ControllerState.class;
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
    static int bindingHashCode(final org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.@NonNull ControllerState obj) {
        int result = 1;
        final int prime = 31;
        result = prime * result + Objects.hashCode(obj.getBgpLsNode());
        result = prime * result + Objects.hashCode(obj.getCacheState());
        result = prime * result + Objects.hashCode(obj.getControlPlane());
        result = prime * result + Objects.hashCode(obj.getGeneratedAt());
        result = prime * result + Objects.hashCode(obj.getProcessedPacketCount());
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
    static boolean bindingEquals(final org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.@NonNull ControllerState thisObj, final Object obj) {
        if (thisObj == obj) {
            return true;
        }
        final var other = CodeHelpers.checkCast(org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.ControllerState.class, obj);
        return other != null
            && Objects.equals(thisObj.getProcessedPacketCount(), other.getProcessedPacketCount())
            && Objects.equals(thisObj.getGeneratedAt(), other.getGeneratedAt())
            && Objects.equals(thisObj.getBgpLsNode(), other.getBgpLsNode())
            && Objects.equals(thisObj.getCacheState(), other.getCacheState())
            && Objects.equals(thisObj.getControlPlane(), other.getControlPlane())
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
    static String bindingToString(final org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.@NonNull ControllerState obj) {
        final var helper = MoreObjects.toStringHelper("ControllerState");
        CodeHelpers.appendValue(helper, "bgpLsNode", obj.getBgpLsNode());
        CodeHelpers.appendValue(helper, "cacheState", obj.getCacheState());
        CodeHelpers.appendValue(helper, "controlPlane", obj.getControlPlane());
        CodeHelpers.appendValue(helper, "generatedAt", obj.getGeneratedAt());
        CodeHelpers.appendValue(helper, "processedPacketCount", obj.getProcessedPacketCount());
        CodeHelpers.appendAugmentations(helper, "augmentation", obj);
        return helper.toString();
    }
    
    /**
     * Return generatedAt, or {@code null} if it is not present.
     *
     * <pre>
     *     <code>
     *         UTC instant at which this snapshot was generated.
     *     </code>
     * </pre>
     *
     * @return {@code String} generatedAt, or {@code null} if it is not present.
     *
     */
    String getGeneratedAt();
    
    /**
     * Return generatedAt, guaranteed to be non-null.
     *
     * <pre>
     *     <code>
     *         UTC instant at which this snapshot was generated.
     *     </code>
     * </pre>
     *
     * @return {@code String} generatedAt, guaranteed to be non-null.
     * @throws NoSuchElementException if generatedAt is not present
     *
     */
    default @NonNull String requireGeneratedAt() {
        return CodeHelpers.require(getGeneratedAt(), "generatedat");
    }
    
    /**
     * Return processedPacketCount, or {@code null} if it is not present.
     *
     * @return {@code Uint64} processedPacketCount, or {@code null} if it is not present.
     *
     */
    Uint64 getProcessedPacketCount();
    
    /**
     * Return processedPacketCount, guaranteed to be non-null.
     *
     * @return {@code Uint64} processedPacketCount, guaranteed to be non-null.
     * @throws NoSuchElementException if processedPacketCount is not present
     *
     */
    default @NonNull Uint64 requireProcessedPacketCount() {
        return CodeHelpers.require(getProcessedPacketCount(), "processedpacketcount");
    }
    
    /**
     * Return controlPlane, or {@code null} if it is not present.
     *
     * @return {@code ControlPlane} controlPlane, or {@code null} if it is not present.
     *
     */
    ControlPlane getControlPlane();
    
    /**
     * Return controlPlane, or an empty instance if it is not present.
     *
     * @return {@code ControlPlane} controlPlane, or an empty instance if it is not present.
     *
     */
    @NonNull ControlPlane nonnullControlPlane();
    
    /**
     * Return cacheState, or {@code null} if it is not present.
     *
     * @return {@code CacheState} cacheState, or {@code null} if it is not present.
     *
     */
    CacheState getCacheState();
    
    /**
     * Return cacheState, or an empty instance if it is not present.
     *
     * @return {@code CacheState} cacheState, or an empty instance if it is not present.
     *
     */
    @NonNull CacheState nonnullCacheState();
    
    /**
     * Return bgpLsNode, or {@code null} if it is not present.
     *
     * @return {@code Map<BgpLsNodeKey, BgpLsNode>} bgpLsNode, or {@code null} if it is not present.
     *
     */
    @Nullable Map<BgpLsNodeKey, BgpLsNode> getBgpLsNode();
    
    /**
     * Return bgpLsNode, or an empty list if it is not present.
     *
     * @return {@code Map<BgpLsNodeKey, BgpLsNode>} bgpLsNode, or an empty list if it is not present.
     *
     */
    default @NonNull Map<BgpLsNodeKey, BgpLsNode> nonnullBgpLsNode() {
        return CodeHelpers.nonnull(getBgpLsNode());
    }

}

