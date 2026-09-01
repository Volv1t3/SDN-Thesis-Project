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
import org.opendaylight.yangtools.binding.Augmentable;
import org.opendaylight.yangtools.binding.ChildOf;
import org.opendaylight.yangtools.binding.lib.CodeHelpers;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 *
 * <p>
 * This class represents the following YANG schema fragment defined in module <b>csa</b>
 * <pre>
 * container cache-state {
 *   leaf classification-entry-count {
 *     type uint32;
 *   }
 *   leaf calculated-path-entry-count {
 *     type uint32;
 *   }
 *   leaf policy-evidence-bucket-count {
 *     type uint32;
 *   }
 *   leaf active-pair-policy-count {
 *     type uint32;
 *   }
 *   leaf delegated-lsp-count {
 *     type uint32;
 *   }
 *   leaf openflow-switch-count {
 *     type uint32;
 *   }
 * }
 * </pre>
 * <p>To create instances of this class use {@link CacheStateBuilder}.
 * @see CacheStateBuilder
 *
 */
@Generated("mdsal-binding-generator")
public interface CacheState
    extends
    ChildOf<ControllerState>,
    Augmentable<CacheState>
{



    /**
     * YANG identifier of the statement represented by this class.
     */
    public static final @NonNull QName QNAME = YangModuleInfoImpl.qnameOf("cache-state");

    @Override
    default Class<org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.CacheState> implementedInterface() {
        return org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.CacheState.class;
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
    static int bindingHashCode(final org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.@NonNull CacheState obj) {
        int result = 1;
        final int prime = 31;
        result = prime * result + Objects.hashCode(obj.getActivePairPolicyCount());
        result = prime * result + Objects.hashCode(obj.getCalculatedPathEntryCount());
        result = prime * result + Objects.hashCode(obj.getClassificationEntryCount());
        result = prime * result + Objects.hashCode(obj.getDelegatedLspCount());
        result = prime * result + Objects.hashCode(obj.getOpenflowSwitchCount());
        result = prime * result + Objects.hashCode(obj.getPolicyEvidenceBucketCount());
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
    static boolean bindingEquals(final org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.@NonNull CacheState thisObj, final Object obj) {
        if (thisObj == obj) {
            return true;
        }
        final var other = CodeHelpers.checkCast(org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.CacheState.class, obj);
        return other != null
            && Objects.equals(thisObj.getActivePairPolicyCount(), other.getActivePairPolicyCount())
            && Objects.equals(thisObj.getCalculatedPathEntryCount(), other.getCalculatedPathEntryCount())
            && Objects.equals(thisObj.getClassificationEntryCount(), other.getClassificationEntryCount())
            && Objects.equals(thisObj.getDelegatedLspCount(), other.getDelegatedLspCount())
            && Objects.equals(thisObj.getOpenflowSwitchCount(), other.getOpenflowSwitchCount())
            && Objects.equals(thisObj.getPolicyEvidenceBucketCount(), other.getPolicyEvidenceBucketCount())
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
    static String bindingToString(final org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.controller.state.@NonNull CacheState obj) {
        final var helper = MoreObjects.toStringHelper("CacheState");
        CodeHelpers.appendValue(helper, "activePairPolicyCount", obj.getActivePairPolicyCount());
        CodeHelpers.appendValue(helper, "calculatedPathEntryCount", obj.getCalculatedPathEntryCount());
        CodeHelpers.appendValue(helper, "classificationEntryCount", obj.getClassificationEntryCount());
        CodeHelpers.appendValue(helper, "delegatedLspCount", obj.getDelegatedLspCount());
        CodeHelpers.appendValue(helper, "openflowSwitchCount", obj.getOpenflowSwitchCount());
        CodeHelpers.appendValue(helper, "policyEvidenceBucketCount", obj.getPolicyEvidenceBucketCount());
        CodeHelpers.appendAugmentations(helper, "augmentation", obj);
        return helper.toString();
    }
    
    /**
     * Return classificationEntryCount, or {@code null} if it is not present.
     *
     * @return {@code Uint32} classificationEntryCount, or {@code null} if it is not present.
     *
     */
    Uint32 getClassificationEntryCount();
    
    /**
     * Return classificationEntryCount, guaranteed to be non-null.
     *
     * @return {@code Uint32} classificationEntryCount, guaranteed to be non-null.
     * @throws NoSuchElementException if classificationEntryCount is not present
     *
     */
    default @NonNull Uint32 requireClassificationEntryCount() {
        return CodeHelpers.require(getClassificationEntryCount(), "classificationentrycount");
    }
    
    /**
     * Return calculatedPathEntryCount, or {@code null} if it is not present.
     *
     * @return {@code Uint32} calculatedPathEntryCount, or {@code null} if it is not present.
     *
     */
    Uint32 getCalculatedPathEntryCount();
    
    /**
     * Return calculatedPathEntryCount, guaranteed to be non-null.
     *
     * @return {@code Uint32} calculatedPathEntryCount, guaranteed to be non-null.
     * @throws NoSuchElementException if calculatedPathEntryCount is not present
     *
     */
    default @NonNull Uint32 requireCalculatedPathEntryCount() {
        return CodeHelpers.require(getCalculatedPathEntryCount(), "calculatedpathentrycount");
    }
    
    /**
     * Return policyEvidenceBucketCount, or {@code null} if it is not present.
     *
     * @return {@code Uint32} policyEvidenceBucketCount, or {@code null} if it is not present.
     *
     */
    Uint32 getPolicyEvidenceBucketCount();
    
    /**
     * Return policyEvidenceBucketCount, guaranteed to be non-null.
     *
     * @return {@code Uint32} policyEvidenceBucketCount, guaranteed to be non-null.
     * @throws NoSuchElementException if policyEvidenceBucketCount is not present
     *
     */
    default @NonNull Uint32 requirePolicyEvidenceBucketCount() {
        return CodeHelpers.require(getPolicyEvidenceBucketCount(), "policyevidencebucketcount");
    }
    
    /**
     * Return activePairPolicyCount, or {@code null} if it is not present.
     *
     * @return {@code Uint32} activePairPolicyCount, or {@code null} if it is not present.
     *
     */
    Uint32 getActivePairPolicyCount();
    
    /**
     * Return activePairPolicyCount, guaranteed to be non-null.
     *
     * @return {@code Uint32} activePairPolicyCount, guaranteed to be non-null.
     * @throws NoSuchElementException if activePairPolicyCount is not present
     *
     */
    default @NonNull Uint32 requireActivePairPolicyCount() {
        return CodeHelpers.require(getActivePairPolicyCount(), "activepairpolicycount");
    }
    
    /**
     * Return delegatedLspCount, or {@code null} if it is not present.
     *
     * @return {@code Uint32} delegatedLspCount, or {@code null} if it is not present.
     *
     */
    Uint32 getDelegatedLspCount();
    
    /**
     * Return delegatedLspCount, guaranteed to be non-null.
     *
     * @return {@code Uint32} delegatedLspCount, guaranteed to be non-null.
     * @throws NoSuchElementException if delegatedLspCount is not present
     *
     */
    default @NonNull Uint32 requireDelegatedLspCount() {
        return CodeHelpers.require(getDelegatedLspCount(), "delegatedlspcount");
    }
    
    /**
     * Return openflowSwitchCount, or {@code null} if it is not present.
     *
     * @return {@code Uint32} openflowSwitchCount, or {@code null} if it is not present.
     *
     */
    Uint32 getOpenflowSwitchCount();
    
    /**
     * Return openflowSwitchCount, guaranteed to be non-null.
     *
     * @return {@code Uint32} openflowSwitchCount, guaranteed to be non-null.
     * @throws NoSuchElementException if openflowSwitchCount is not present
     *
     */
    default @NonNull Uint32 requireOpenflowSwitchCount() {
        return CodeHelpers.require(getOpenflowSwitchCount(), "openflowswitchcount");
    }

}

