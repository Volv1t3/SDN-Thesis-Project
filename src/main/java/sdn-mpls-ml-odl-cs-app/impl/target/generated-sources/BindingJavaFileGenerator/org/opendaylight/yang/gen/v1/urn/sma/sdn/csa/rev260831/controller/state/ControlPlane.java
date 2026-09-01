package org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state;
import com.google.common.base.MoreObjects;
import java.lang.Boolean;
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
import org.opendaylight.yangtools.binding.Augmentable;
import org.opendaylight.yangtools.binding.ChildOf;
import org.opendaylight.yangtools.binding.lib.CodeHelpers;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint64;

/**
 *
 * <p>
 * This class represents the following YANG schema fragment defined in module <b>csa</b>
 * <pre>
 * container control-plane {
 *   leaf ready {
 *     type boolean;
 *   }
 *   leaf closed {
 *     type boolean;
 *   }
 *   leaf topology-id {
 *     type string;
 *   }
 *   leaf topology-ttl-millis {
 *     type uint64;
 *   }
 *   leaf topology-fresh {
 *     type boolean;
 *   }
 *   leaf topology-refresh-in-progress {
 *     type boolean;
 *   }
 *   leaf topology-last-successful-refresh {
 *     type string;
 *   }
 *   leaf topology-last-refresh-attempt {
 *     type string;
 *   }
 *   leaf topology-fresh-until {
 *     type string;
 *   }
 *   leaf topology-last-failure {
 *     type string;
 *   }
 *   leaf topology-refresh-success-count {
 *     type uint64;
 *   }
 *   leaf topology-refresh-failure-count {
 *     type uint64;
 *   }
 * }
 * </pre>
 * <p>To create instances of this class use {@link ControlPlaneBuilder}.
 * @see ControlPlaneBuilder
 *
 */
@Generated("mdsal-binding-generator")
public interface ControlPlane
    extends
    ChildOf<ControllerState>,
    Augmentable<ControlPlane>
{



    /**
     * YANG identifier of the statement represented by this class.
     */
    public static final @NonNull QName QNAME = YangModuleInfoImpl.qnameOf("control-plane");

    @Override
    default Class<org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.ControlPlane> implementedInterface() {
        return org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.ControlPlane.class;
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
    static int bindingHashCode(final org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.@NonNull ControlPlane obj) {
        int result = 1;
        final int prime = 31;
        result = prime * result + Objects.hashCode(obj.getClosed());
        result = prime * result + Objects.hashCode(obj.getReady());
        result = prime * result + Objects.hashCode(obj.getTopologyFresh());
        result = prime * result + Objects.hashCode(obj.getTopologyFreshUntil());
        result = prime * result + Objects.hashCode(obj.getTopologyId());
        result = prime * result + Objects.hashCode(obj.getTopologyLastFailure());
        result = prime * result + Objects.hashCode(obj.getTopologyLastRefreshAttempt());
        result = prime * result + Objects.hashCode(obj.getTopologyLastSuccessfulRefresh());
        result = prime * result + Objects.hashCode(obj.getTopologyRefreshFailureCount());
        result = prime * result + Objects.hashCode(obj.getTopologyRefreshInProgress());
        result = prime * result + Objects.hashCode(obj.getTopologyRefreshSuccessCount());
        result = prime * result + Objects.hashCode(obj.getTopologyTtlMillis());
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
    static boolean bindingEquals(final org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.@NonNull ControlPlane thisObj, final Object obj) {
        if (thisObj == obj) {
            return true;
        }
        final var other = CodeHelpers.checkCast(org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.ControlPlane.class, obj);
        return other != null
            && Objects.equals(thisObj.getClosed(), other.getClosed())
            && Objects.equals(thisObj.getReady(), other.getReady())
            && Objects.equals(thisObj.getTopologyFresh(), other.getTopologyFresh())
            && Objects.equals(thisObj.getTopologyRefreshFailureCount(), other.getTopologyRefreshFailureCount())
            && Objects.equals(thisObj.getTopologyRefreshInProgress(), other.getTopologyRefreshInProgress())
            && Objects.equals(thisObj.getTopologyRefreshSuccessCount(), other.getTopologyRefreshSuccessCount())
            && Objects.equals(thisObj.getTopologyTtlMillis(), other.getTopologyTtlMillis())
            && Objects.equals(thisObj.getTopologyFreshUntil(), other.getTopologyFreshUntil())
            && Objects.equals(thisObj.getTopologyId(), other.getTopologyId())
            && Objects.equals(thisObj.getTopologyLastFailure(), other.getTopologyLastFailure())
            && Objects.equals(thisObj.getTopologyLastRefreshAttempt(), other.getTopologyLastRefreshAttempt())
            && Objects.equals(thisObj.getTopologyLastSuccessfulRefresh(), other.getTopologyLastSuccessfulRefresh())
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
    static String bindingToString(final org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.@NonNull ControlPlane obj) {
        final var helper = MoreObjects.toStringHelper("ControlPlane");
        CodeHelpers.appendValue(helper, "closed", obj.getClosed());
        CodeHelpers.appendValue(helper, "ready", obj.getReady());
        CodeHelpers.appendValue(helper, "topologyFresh", obj.getTopologyFresh());
        CodeHelpers.appendValue(helper, "topologyFreshUntil", obj.getTopologyFreshUntil());
        CodeHelpers.appendValue(helper, "topologyId", obj.getTopologyId());
        CodeHelpers.appendValue(helper, "topologyLastFailure", obj.getTopologyLastFailure());
        CodeHelpers.appendValue(helper, "topologyLastRefreshAttempt", obj.getTopologyLastRefreshAttempt());
        CodeHelpers.appendValue(helper, "topologyLastSuccessfulRefresh", obj.getTopologyLastSuccessfulRefresh());
        CodeHelpers.appendValue(helper, "topologyRefreshFailureCount", obj.getTopologyRefreshFailureCount());
        CodeHelpers.appendValue(helper, "topologyRefreshInProgress", obj.getTopologyRefreshInProgress());
        CodeHelpers.appendValue(helper, "topologyRefreshSuccessCount", obj.getTopologyRefreshSuccessCount());
        CodeHelpers.appendValue(helper, "topologyTtlMillis", obj.getTopologyTtlMillis());
        CodeHelpers.appendAugmentations(helper, "augmentation", obj);
        return helper.toString();
    }
    
    /**
     * Return ready, or {@code null} if it is not present.
     *
     * @return {@code Boolean} ready, or {@code null} if it is not present.
     *
     */
    Boolean getReady();
    
    /**
     * Return ready, guaranteed to be non-null.
     *
     * @return {@code Boolean} ready, guaranteed to be non-null.
     * @throws NoSuchElementException if ready is not present
     *
     */
    default @NonNull Boolean requireReady() {
        return CodeHelpers.require(getReady(), "ready");
    }
    
    /**
     * Return closed, or {@code null} if it is not present.
     *
     * @return {@code Boolean} closed, or {@code null} if it is not present.
     *
     */
    Boolean getClosed();
    
    /**
     * Return closed, guaranteed to be non-null.
     *
     * @return {@code Boolean} closed, guaranteed to be non-null.
     * @throws NoSuchElementException if closed is not present
     *
     */
    default @NonNull Boolean requireClosed() {
        return CodeHelpers.require(getClosed(), "closed");
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
     * Return topologyTtlMillis, or {@code null} if it is not present.
     *
     * @return {@code Uint64} topologyTtlMillis, or {@code null} if it is not present.
     *
     */
    Uint64 getTopologyTtlMillis();
    
    /**
     * Return topologyTtlMillis, guaranteed to be non-null.
     *
     * @return {@code Uint64} topologyTtlMillis, guaranteed to be non-null.
     * @throws NoSuchElementException if topologyTtlMillis is not present
     *
     */
    default @NonNull Uint64 requireTopologyTtlMillis() {
        return CodeHelpers.require(getTopologyTtlMillis(), "topologyttlmillis");
    }
    
    /**
     * Return topologyFresh, or {@code null} if it is not present.
     *
     * @return {@code Boolean} topologyFresh, or {@code null} if it is not present.
     *
     */
    Boolean getTopologyFresh();
    
    /**
     * Return topologyFresh, guaranteed to be non-null.
     *
     * @return {@code Boolean} topologyFresh, guaranteed to be non-null.
     * @throws NoSuchElementException if topologyFresh is not present
     *
     */
    default @NonNull Boolean requireTopologyFresh() {
        return CodeHelpers.require(getTopologyFresh(), "topologyfresh");
    }
    
    /**
     * Return topologyRefreshInProgress, or {@code null} if it is not present.
     *
     * @return {@code Boolean} topologyRefreshInProgress, or {@code null} if it is not present.
     *
     */
    Boolean getTopologyRefreshInProgress();
    
    /**
     * Return topologyRefreshInProgress, guaranteed to be non-null.
     *
     * @return {@code Boolean} topologyRefreshInProgress, guaranteed to be non-null.
     * @throws NoSuchElementException if topologyRefreshInProgress is not present
     *
     */
    default @NonNull Boolean requireTopologyRefreshInProgress() {
        return CodeHelpers.require(getTopologyRefreshInProgress(), "topologyrefreshinprogress");
    }
    
    /**
     * Return topologyLastSuccessfulRefresh, or {@code null} if it is not present.
     *
     * @return {@code String} topologyLastSuccessfulRefresh, or {@code null} if it is not present.
     *
     */
    String getTopologyLastSuccessfulRefresh();
    
    /**
     * Return topologyLastSuccessfulRefresh, guaranteed to be non-null.
     *
     * @return {@code String} topologyLastSuccessfulRefresh, guaranteed to be non-null.
     * @throws NoSuchElementException if topologyLastSuccessfulRefresh is not present
     *
     */
    default @NonNull String requireTopologyLastSuccessfulRefresh() {
        return CodeHelpers.require(getTopologyLastSuccessfulRefresh(), "topologylastsuccessfulrefresh");
    }
    
    /**
     * Return topologyLastRefreshAttempt, or {@code null} if it is not present.
     *
     * @return {@code String} topologyLastRefreshAttempt, or {@code null} if it is not present.
     *
     */
    String getTopologyLastRefreshAttempt();
    
    /**
     * Return topologyLastRefreshAttempt, guaranteed to be non-null.
     *
     * @return {@code String} topologyLastRefreshAttempt, guaranteed to be non-null.
     * @throws NoSuchElementException if topologyLastRefreshAttempt is not present
     *
     */
    default @NonNull String requireTopologyLastRefreshAttempt() {
        return CodeHelpers.require(getTopologyLastRefreshAttempt(), "topologylastrefreshattempt");
    }
    
    /**
     * Return topologyFreshUntil, or {@code null} if it is not present.
     *
     * @return {@code String} topologyFreshUntil, or {@code null} if it is not present.
     *
     */
    String getTopologyFreshUntil();
    
    /**
     * Return topologyFreshUntil, guaranteed to be non-null.
     *
     * @return {@code String} topologyFreshUntil, guaranteed to be non-null.
     * @throws NoSuchElementException if topologyFreshUntil is not present
     *
     */
    default @NonNull String requireTopologyFreshUntil() {
        return CodeHelpers.require(getTopologyFreshUntil(), "topologyfreshuntil");
    }
    
    /**
     * Return topologyLastFailure, or {@code null} if it is not present.
     *
     * @return {@code String} topologyLastFailure, or {@code null} if it is not present.
     *
     */
    String getTopologyLastFailure();
    
    /**
     * Return topologyLastFailure, guaranteed to be non-null.
     *
     * @return {@code String} topologyLastFailure, guaranteed to be non-null.
     * @throws NoSuchElementException if topologyLastFailure is not present
     *
     */
    default @NonNull String requireTopologyLastFailure() {
        return CodeHelpers.require(getTopologyLastFailure(), "topologylastfailure");
    }
    
    /**
     * Return topologyRefreshSuccessCount, or {@code null} if it is not present.
     *
     * @return {@code Uint64} topologyRefreshSuccessCount, or {@code null} if it is not present.
     *
     */
    Uint64 getTopologyRefreshSuccessCount();
    
    /**
     * Return topologyRefreshSuccessCount, guaranteed to be non-null.
     *
     * @return {@code Uint64} topologyRefreshSuccessCount, guaranteed to be non-null.
     * @throws NoSuchElementException if topologyRefreshSuccessCount is not present
     *
     */
    default @NonNull Uint64 requireTopologyRefreshSuccessCount() {
        return CodeHelpers.require(getTopologyRefreshSuccessCount(), "topologyrefreshsuccesscount");
    }
    
    /**
     * Return topologyRefreshFailureCount, or {@code null} if it is not present.
     *
     * @return {@code Uint64} topologyRefreshFailureCount, or {@code null} if it is not present.
     *
     */
    Uint64 getTopologyRefreshFailureCount();
    
    /**
     * Return topologyRefreshFailureCount, guaranteed to be non-null.
     *
     * @return {@code Uint64} topologyRefreshFailureCount, guaranteed to be non-null.
     * @throws NoSuchElementException if topologyRefreshFailureCount is not present
     *
     */
    default @NonNull Uint64 requireTopologyRefreshFailureCount() {
        return CodeHelpers.require(getTopologyRefreshFailureCount(), "topologyrefreshfailurecount");
    }

}

