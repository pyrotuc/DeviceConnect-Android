/*
 HumanDetectBodyRequestParams.java
 Copyright (c) 2015 NTT DOCOMO,INC.
 Released under the MIT license
 http://opensource.org/licenses/mit-license.php
 */
package org.deviceconnect.android.deviceplugin.hvc.humandetect;

/**
 * body detect request parameter class.
 * 
 * @author NTT DOCOMO, INC.
 */
public class HumanDetectBodyRequestParams extends HumanDetectBasicRequestParams {

    /**
     * Constructor(with default value).
     * @param normalizeThreshold threshold
     * @param normalizeMinWidth minWidth
     * @param normalizeMinHeight minHeight
     * @param normalizeMaxWidth maxWidth
     * @param normalizeMaxHeight maxHeight
     */
    public HumanDetectBodyRequestParams(final double normalizeThreshold, final double normalizeMinWidth,
            final double normalizeMinHeight, final double normalizeMaxWidth, final double normalizeMaxHeight) {
        super(normalizeThreshold, normalizeMinWidth, normalizeMinHeight, normalizeMaxWidth, normalizeMaxHeight);
        
    }

}
