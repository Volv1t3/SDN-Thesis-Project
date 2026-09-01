package org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831;
import java.lang.Class;
import java.lang.Override;
import javax.annotation.processing.Generated;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.binding.DataRoot;

/**
 * Read-only operational state for the SDN-MPLS-ML controller side application.
 *
 * <p>
 * This class represents the following YANG schema fragment defined in module <b>csa</b>
 * <pre>
 * module csa {
 *   yang-version 1.1;
 *   namespace urn:sma:sdn:csa;
 *   prefix csa;
 *   revision 2026-08-31 {
 *   }
 *   container controller-state {
 *     config false;
 *     leaf generated-at {
 *       type string;
 *     }
 *     leaf processed-packet-count {
 *       type uint64;
 *     }
 *     container control-plane {
 *       leaf ready {
 *         type boolean;
 *       }
 *       leaf closed {
 *         type boolean;
 *       }
 *       leaf topology-id {
 *         type string;
 *       }
 *       leaf topology-ttl-millis {
 *         type uint64;
 *       }
 *       leaf topology-fresh {
 *         type boolean;
 *       }
 *       leaf topology-refresh-in-progress {
 *         type boolean;
 *       }
 *       leaf topology-last-successful-refresh {
 *         type string;
 *       }
 *       leaf topology-last-refresh-attempt {
 *         type string;
 *       }
 *       leaf topology-fresh-until {
 *         type string;
 *       }
 *       leaf topology-last-failure {
 *         type string;
 *       }
 *       leaf topology-refresh-success-count {
 *         type uint64;
 *       }
 *       leaf topology-refresh-failure-count {
 *         type uint64;
 *       }
 *     }
 *     container cache-state {
 *       leaf classification-entry-count {
 *         type uint32;
 *       }
 *       leaf calculated-path-entry-count {
 *         type uint32;
 *       }
 *       leaf policy-evidence-bucket-count {
 *         type uint32;
 *       }
 *       leaf active-pair-policy-count {
 *         type uint32;
 *       }
 *       leaf delegated-lsp-count {
 *         type uint32;
 *       }
 *       leaf openflow-switch-count {
 *         type uint32;
 *       }
 *     }
 *     list bgp-ls-node {
 *       key router-id;
 *       leaf router-id {
 *         type string;
 *       }
 *       leaf topology-id {
 *         type string;
 *       }
 *       leaf node-id {
 *         type string;
 *       }
 *       leaf te-router-id-ipv4 {
 *         type string;
 *       }
 *       leaf graph-node-id {
 *         type uint64;
 *       }
 *     }
 *   }
 * }
 * </pre>
 *
 */
@Generated("mdsal-binding-generator")
public interface CsaData
    extends
    DataRoot<CsaData>
{




    @Override
    default Class<org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.CsaData> implementedInterface() {
        return org.opendaylight.yang.gen.v1.urn.sma.sdn.csa.rev260831.CsaData.class;
    }
    
    /**
     * Return controllerState, or {@code null} if it is not present.
     *
     * <pre>
     *     <code>
     *         Current controller-side application state for external validation.
     *     </code>
     * </pre>
     *
     * @return {@code ControllerState} controllerState, or {@code null} if it is not present.
     *
     */
    ControllerState getControllerState();
    
    /**
     * Return controllerState, or an empty instance if it is not present.
     *
     * @return {@code ControllerState} controllerState, or an empty instance if it is not present.
     *
     */
    @NonNull ControllerState nonnullControllerState();

}

