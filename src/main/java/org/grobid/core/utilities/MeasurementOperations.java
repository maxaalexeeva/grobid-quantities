package org.grobid.core.utilities;

import org.grobid.core.GrobidModels;
import org.grobid.core.data.Quantity;
import org.grobid.core.data.Unit;
import org.grobid.core.data.Measurement;
import org.grobid.core.data.UnitDefinition;
import org.grobid.core.data.normalization.UnitNormalizer;
import org.grobid.core.engines.TaggingLabel;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.lexicon.QuantityLexicon;

import java.util.ArrayList;
import java.util.List;

import org.grobid.core.tokenization.TaggingTokenCluster;
import org.grobid.core.tokenization.TaggingTokenClusteror;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Try to resolveMeasurement measurement extracted attributes (unit, values).
 *
 * @author Patrice Lopez
 */
public class MeasurementOperations {
    private static final Logger logger = LoggerFactory.getLogger(MeasurementOperations.class);

    UnitNormalizer un = new UnitNormalizer();

    public MeasurementOperations() {
    }

    /**
     * Check if the given list of measures are well formed:
     * In particular, if intervals are not consistent, they are transformed
     * in atomic value measurements.
     */
    public static List<Measurement> postCorrection(List<Measurement> measurements) {
        List<Measurement> newMeasurements = new ArrayList<>();
        for (Measurement measurement : measurements) {
            if (measurement.getType() == UnitUtilities.Measurement_Type.VALUE) {
                Quantity quantity = measurement.getQuantityAtomic();
                if (quantity == null)
                    continue;
                // if the unit is too far from the value, the measurement needs to be filtered out
                int start = quantity.getOffsetStart();
                int end = quantity.getOffsetEnd();
                Unit rawUnit = quantity.getRawUnit();
                if (rawUnit != null) {
                    int startU = rawUnit.getOffsetStart();
                    int endU = rawUnit.getOffsetEnd();
                    if ((Math.abs(end - startU) < 40) || (Math.abs(endU - start) < 40)) {
                        newMeasurements.add(measurement);
                    }
                } else
                    newMeasurements.add(measurement);

            } else if ( (measurement.getType() == UnitUtilities.Measurement_Type.INTERVAL_MIN_MAX) ||
                        (measurement.getType() == UnitUtilities.Measurement_Type.INTERVAL_BASE_RANGE) ) {
                // values of the interval do not matter if min/max or base/range
                Quantity quantityLeast = measurement.getQuantityLeast();
                if (quantityLeast == null)
                    quantityLeast = measurement.getQuantityBase();
                Quantity quantityMost = measurement.getQuantityMost();
                if (quantityMost == null)
                    quantityMost = measurement.getQuantityRange();

                if ((quantityLeast == null) && (quantityMost == null))
                    continue;
                /*if (((quantityLeast != null) && (quantityMost == null)) || ((quantityLeast == null) && (quantityMost != null))) {
                    Measurement newMeasurement = new Measurement(UnitUtilities.Measurement_Type.VALUE);
                    Quantity quantity = null;
                    if (quantityLeast != null) {
                        quantity = quantityLeast;
                    } else if (quantityMost != null) {
                        quantity = quantityMost;
                    }
                    if (quantity != null) {
                        newMeasurement.setAtomicQuantity(quantity);
                        newMeasurements.add(newMeasurement);
                    }
                } else*/ if ((quantityLeast != null) && (quantityMost != null)) {
                    // if the interval is expressed over a chunck of text which is too large, it is a recognition error
                    // and we can replace it by two atomic measurements
                    int startL = quantityLeast.getOffsetStart();
                    int endL = quantityLeast.getOffsetEnd();
                    int startM = quantityMost.getOffsetStart();
                    int endM = quantityMost.getOffsetEnd();

                    if ((Math.abs(endL - startM) > 80) && (Math.abs(endM - startL) > 80)) {
                        // we replace the interval measurement by two atomic measurements
                        Measurement newMeasurement = new Measurement(UnitUtilities.Measurement_Type.VALUE);
                        // have to check the position of value and unit for valid atomic measure
                        Unit rawUnit = quantityLeast.getRawUnit();
                        if (rawUnit != null) {
                            int startU = rawUnit.getOffsetStart();
                            int endU = rawUnit.getOffsetEnd();
                            if ((Math.abs(endL - startU) < 40) || (Math.abs(endU - startL) < 40)) {
                                newMeasurement.setAtomicQuantity(quantityLeast);
                                newMeasurements.add(newMeasurement);
                            }
                        } else {
                            newMeasurement.setAtomicQuantity(quantityLeast);
                            newMeasurements.add(newMeasurement);
                        }

                        newMeasurement = new Measurement(UnitUtilities.Measurement_Type.VALUE);
                        rawUnit = quantityMost.getRawUnit();
                        if (rawUnit != null) {
                            int startU = rawUnit.getOffsetStart();
                            int endU = rawUnit.getOffsetEnd();
                            if ((Math.abs(endM - startU) < 40) || (Math.abs(endU - startM) < 40)) {
                                newMeasurement.setAtomicQuantity(quantityMost);
                                newMeasurements.add(newMeasurement);
                            }
                        } else {
                            newMeasurement.setAtomicQuantity(quantityMost);
                            newMeasurements.add(newMeasurement);
                        }

                    } else
                        newMeasurements.add(measurement);
                } else
                    newMeasurements.add(measurement);
            } else if (measurement.getType() == UnitUtilities.Measurement_Type.CONJUNCTION) {
                // list must be consistent in unit type, and avoid too large chunk
                List<Quantity> quantities = measurement.getQuantityList();
                if ((quantities == null) || (quantities.size() == 0))
                    continue;

                /* // case actually not seen
                Unit currentUnit = null;
                Measurement newMeasurement = new Measurement(UnitUtilities.Measurement_Type.CONJUNCTION);
                for(Quantity quantity : quantities) {
                    if (currentUnit == null)
                        currentUnit = quantity.getRawUnit();
                    else {
                        Unit newUnit = quantity.getRawUnit();

                        // is it a new unit?
                        if ((currentUnit != null) && (newUnit != null) && (!currentUnit.getRawName().equals(newUnit.getRawName())) ) {
                             // we have a new unit, so we split the list
                            if ( (newMeasurement != null) && (newMeasurement.getQuantities() != null) && (newMeasurement.getQuantities().size() > 0) )
                                newMeasurements.add(newMeasurement);
                            newMeasurement = new Measurement(UnitUtilities.Measurement_Type.CONJUNCTION);
                            newMeasurement.addQuantity(quantity);
                            currentUnit = newUnit;
                        }
                        else {
                            // same unit, we extend the current list
                            newMeasurement.addQuantity(quantity);
                        }
                    }

                }
                if ( (newMeasurement != null) && (newMeasurement.getQuantities() != null) && (newMeasurement.getQuantities().size() > 0) )
                    newMeasurements.add(newMeasurement);*/

                // the case of atomic values within list should be cover here
                // in this case, we have a list followed by an atomic value, then a following list without unit attachment, with possibly
                // only one quantity - the correction is to extend the starting list with the remaining list after the atomic value, attaching
                // the unit associated to the starting list to the added quantities


                newMeasurements.add(measurement);
            }
        }

        return newMeasurements;
    }


    /**
     * Right now, only basic matching of units based on lexicon look-up and value validation
     * via regex.
     */
    public List<Measurement> resolveMeasurement(List<Measurement> measurements) {
        for (Measurement measurement : measurements) {
            if (measurement.getType() == null)
                continue;
            else if (measurement.getType() == UnitUtilities.Measurement_Type.VALUE) {
                updateQuantity(measurement.getQuantityAtomic());
            } else if (measurement.getType() == UnitUtilities.Measurement_Type.INTERVAL_MIN_MAX) {
                updateQuantity(measurement.getQuantityLeast());
                updateQuantity(measurement.getQuantityMost());
            } else if (measurement.getType() == UnitUtilities.Measurement_Type.INTERVAL_BASE_RANGE) {
                updateQuantity(measurement.getQuantityBase());
                updateQuantity(measurement.getQuantityRange());
                // the two quantities bellow are normally not yet set-up
                //updateQuantity(measurement.getQuantityLeast());
                //updateQuantity(measurement.getQuantityMost());
            } else if (measurement.getType() == UnitUtilities.Measurement_Type.CONJUNCTION) {
                if (measurement.getQuantityList() != null) {
                    for (Quantity quantity : measurement.getQuantityList()) {
                        if (quantity == null)
                            continue;
                        updateQuantity(quantity);
                    }
                }
            }
        }
        return measurements;
    }
    private void updateQuantity(Quantity quantity) {
        if ((quantity != null) && (!quantity.isEmpty())) {
            Unit rawUnit = quantity.getRawUnit();
            UnitDefinition foundUnit  = un.findDefinition(rawUnit);

            if (foundUnit != null) {
                rawUnit.setUnitDefinition(foundUnit);
            }
        }
    }



    /**
     * Extract identified quantities from a labelled text.
     */
    public List<Measurement> extractMeasurement(String text,
                                                String result,
                                                List<LayoutToken> tokenizations) {
        List<Measurement> measurements = new ArrayList<>();

        TaggingTokenClusteror clusteror = new TaggingTokenClusteror(GrobidModels.QUANTITIES, result, tokenizations);
        List<TaggingTokenCluster> clusters = clusteror.cluster();

        Unit currentUnit = new Unit();
        Measurement currentMeasurement = new Measurement();
        UnitUtilities.Measurement_Type openMeasurement = null;

        int pos = 0; // position in term of characters for creating the offsets

        for (TaggingTokenCluster cluster : clusters) {
            if (cluster == null) {
                continue;
            }

            TaggingLabel clusterLabel = cluster.getTaggingLabel();
            List<LayoutToken> theTokens = cluster.concatTokens();
            String clusterContent = LayoutTokensUtil.toText(cluster.concatTokens()).trim();

            int endPos = pos;
            for (LayoutToken token : theTokens) {
                if (token.getText() != null)
                    endPos += token.getText().length();
            }
            Quantity currentQuantity = null;

            switch (clusterLabel) {
                case QUANTITY_VALUE_ATOMIC:
                    System.out.println("atomic value: " + clusterContent);
                    if (isMeasurementValid(currentMeasurement)) {
                        measurements.add(currentMeasurement);
                        currentMeasurement = new Measurement();
                        currentUnit = new Unit();
                    }
                    currentQuantity = new Quantity();
                    currentQuantity.setValue(clusterContent);
                    adjustStartOffset(text, pos);
                    currentQuantity.setOffsetStart(pos);
                    endPos = adjustEndOffset(text, endPos);
                    currentQuantity.setOffsetEnd(endPos);
                    currentMeasurement.setType(UnitUtilities.Measurement_Type.VALUE);
                    if (currentUnit.getRawName() != null) {
                        currentQuantity.setRawUnit(currentUnit);
                        currentMeasurement.setAtomicQuantity(currentQuantity);
                        measurements.add(currentMeasurement);
                        currentMeasurement = new Measurement();
                        currentUnit = new Unit();
                        openMeasurement = null;
                    } else {
                        // unit will be attached later
                        currentMeasurement.setAtomicQuantity(currentQuantity);
                        openMeasurement = UnitUtilities.Measurement_Type.VALUE;
                    }
                    break;
                case QUANTITY_VALUE_LEAST:
                    System.out.println("value least: " + clusterContent);
                    if ((openMeasurement != null) && (openMeasurement != UnitUtilities.Measurement_Type.INTERVAL_MIN_MAX)) {
                        if (isMeasurementValid(currentMeasurement)) {
                            measurements.add(currentMeasurement);
                            currentMeasurement = new Measurement();
                            currentUnit = new Unit();
                        }
                    }
                    currentQuantity = new Quantity();
                    currentQuantity.setValue(clusterContent);
                    adjustStartOffset(text, pos);
                    currentQuantity.setOffsetStart(pos);
                    endPos = adjustEndOffset(text, endPos);
                    currentQuantity.setOffsetEnd(endPos);
                    if (currentUnit.getRawName() != null)
                        currentQuantity.setRawUnit(currentUnit);
                    currentMeasurement.setQuantityLeast(currentQuantity);
                    currentMeasurement.setType(UnitUtilities.Measurement_Type.INTERVAL_MIN_MAX);
                    openMeasurement = UnitUtilities.Measurement_Type.INTERVAL_MIN_MAX;
                    break;
                case QUANTITY_VALUE_MOST:
                    System.out.println("value most: " + clusterContent);
                    if ((openMeasurement != null) && (openMeasurement != UnitUtilities.Measurement_Type.INTERVAL_MIN_MAX)) {
                        if (isMeasurementValid(currentMeasurement)) {
                            measurements.add(currentMeasurement);
                            currentMeasurement = new Measurement();
                            currentUnit = new Unit();
                        }
                    }
                    currentQuantity = new Quantity();
                    currentQuantity.setValue(clusterContent);
                    adjustStartOffset(text, pos);
                    currentQuantity.setOffsetStart(pos);
                    endPos = adjustEndOffset(text, endPos);
                    currentQuantity.setOffsetEnd(endPos);
                    if (currentUnit.getRawName() != null) {
                        currentQuantity.setRawUnit(currentUnit);
                    }
                    currentMeasurement.setQuantityMost(currentQuantity);
                    currentMeasurement.setType(UnitUtilities.Measurement_Type.INTERVAL_MIN_MAX);
                    openMeasurement = UnitUtilities.Measurement_Type.INTERVAL_MIN_MAX;
                    break;
                case QUANTITY_VALUE_BASE:
                    System.out.println("base value: " + clusterContent);
                    if ((openMeasurement != null) && (openMeasurement != UnitUtilities.Measurement_Type.INTERVAL_BASE_RANGE)) {
                        if (isMeasurementValid(currentMeasurement)) {
                            measurements.add(currentMeasurement);
                            currentMeasurement = new Measurement();
                            currentUnit = new Unit();
                        }
                    }
                    currentQuantity = new Quantity();
                    currentQuantity.setValue(clusterContent);
                    adjustStartOffset(text, pos);
                    currentQuantity.setOffsetStart(pos);
                    endPos = adjustEndOffset(text, endPos);
                    currentQuantity.setOffsetEnd(endPos);
                    if (currentUnit.getRawName() != null) {
                        currentQuantity.setRawUnit(currentUnit);
                    }
                    currentMeasurement.setQuantityBase(currentQuantity);
                    currentMeasurement.setType(UnitUtilities.Measurement_Type.INTERVAL_BASE_RANGE);
                    openMeasurement = UnitUtilities.Measurement_Type.INTERVAL_BASE_RANGE;
                    break;
                case QUANTITY_VALUE_RANGE:
                    System.out.println("range value: " + clusterContent);
                    if ((openMeasurement != null) && (openMeasurement != UnitUtilities.Measurement_Type.INTERVAL_BASE_RANGE)) {
                        if (isMeasurementValid(currentMeasurement)) {
                            measurements.add(currentMeasurement);
                            currentMeasurement = new Measurement();
                            currentUnit = new Unit();
                        }
                    }
                    currentQuantity = new Quantity();
                    currentQuantity.setValue(clusterContent);
                    adjustStartOffset(text, pos);
                    currentQuantity.setOffsetStart(pos);
                    endPos = adjustEndOffset(text, endPos);
                    currentQuantity.setOffsetEnd(endPos);
                    if (currentUnit.getRawName() != null) {
                        currentQuantity.setRawUnit(currentUnit);
                    }
                    currentMeasurement.setQuantityRange(currentQuantity);
                    currentMeasurement.setType(UnitUtilities.Measurement_Type.INTERVAL_BASE_RANGE);
                    openMeasurement = UnitUtilities.Measurement_Type.INTERVAL_BASE_RANGE;
                    break;
                case QUANTITY_VALUE_LIST:
                    System.out.println("value in list: " + clusterContent);
                    if ((openMeasurement != null) && (openMeasurement != UnitUtilities.Measurement_Type.CONJUNCTION)) {
                        if (isMeasurementValid(currentMeasurement)) {
                            measurements.add(currentMeasurement);
                            currentMeasurement = new Measurement();
                            //currentUnit = new Unit();
                        }
                    }
                    currentQuantity = new Quantity();
                    currentQuantity.setValue(clusterContent);
                    pos = adjustStartOffset(text, pos);
                    currentQuantity.setOffsetStart(pos);
                    endPos = adjustEndOffset(text, endPos);
                    currentQuantity.setOffsetEnd(endPos);

                    if (currentUnit.getRawName() != null) {
                        currentQuantity.setRawUnit(currentUnit);
                    }
                    currentMeasurement.addQuantityList(currentQuantity);
                    currentMeasurement.setType(UnitUtilities.Measurement_Type.CONJUNCTION);
                    openMeasurement = UnitUtilities.Measurement_Type.CONJUNCTION;
                    break;
                case QUANTITY_UNIT_LEFT:
                    System.out.println("unit (left attachment): " + clusterContent);
                    currentUnit = new Unit();
                    currentUnit.setRawName(clusterContent);
                    pos = adjustStartOffset(text, pos);
                    currentUnit.setOffsetStart(pos);
                    endPos = adjustEndOffset(text, endPos);
                    currentUnit.setOffsetEnd(endPos);

                    if (openMeasurement == UnitUtilities.Measurement_Type.VALUE) {
                        if (currentMeasurement.getQuantityAtomic() != null)
                            currentMeasurement.getQuantityAtomic().setRawUnit(currentUnit);
                    } else if (openMeasurement == UnitUtilities.Measurement_Type.INTERVAL_MIN_MAX) {
                        if ((currentMeasurement.getQuantityMost() != null) &&
                                ((currentMeasurement.getQuantityMost().getRawUnit() == null) || (currentMeasurement.getQuantityMost().getRawUnit().getRawName() == null))) {
                            currentMeasurement.getQuantityMost().setRawUnit(currentUnit);
                            if ((currentMeasurement.getQuantityLeast() != null) &&
                                    ((currentMeasurement.getQuantityLeast().getRawUnit() == null) || (currentMeasurement.getQuantityLeast().getRawUnit().getRawName() == null)))
                                currentMeasurement.getQuantityLeast().setRawUnit(currentUnit);
                        }
                    } else if (openMeasurement == UnitUtilities.Measurement_Type.INTERVAL_BASE_RANGE) {
                        if ((currentMeasurement.getQuantityRange() != null) &&
                                ((currentMeasurement.getQuantityRange().getRawUnit() == null) || (currentMeasurement.getQuantityRange().getRawUnit().getRawName() == null))) {
                            currentMeasurement.getQuantityRange().setRawUnit(currentUnit);
                            if ((currentMeasurement.getQuantityBase() != null) &&
                                    ((currentMeasurement.getQuantityBase().getRawUnit() == null) || (currentMeasurement.getQuantityBase().getRawUnit().getRawName() == null)))
                                currentMeasurement.getQuantityBase().setRawUnit(currentUnit);
                        }
                    } else if (openMeasurement == UnitUtilities.Measurement_Type.CONJUNCTION) {
                        if ((currentMeasurement.getQuantityList() != null) && (currentMeasurement.getQuantityList().size() > 0)) {
                            for (Quantity quantity : currentMeasurement.getQuantityList()) {
                                if ((quantity != null) && ((quantity.getRawUnit() == null) || (quantity.getRawUnit().getRawName() == null))) {
                                    quantity.setRawUnit(currentUnit);
                                } else if ((quantity == null) && (openMeasurement == UnitUtilities.Measurement_Type.INTERVAL_MIN_MAX)) {
                                    // we skip the least value, but we can still for robustness attach the unit to the upper range quantity
                                } else
                                    break;
                            }
                        }
                    }
                    currentUnit = new Unit();
                    if (openMeasurement == UnitUtilities.Measurement_Type.VALUE) {
                        if (isMeasurementValid(currentMeasurement)) {
                            measurements.add(currentMeasurement);
                            currentMeasurement = new Measurement();
                            openMeasurement = null;
                        }
                    } else if (openMeasurement == UnitUtilities.Measurement_Type.INTERVAL_MIN_MAX) {
                        if (isMeasurementValid(currentMeasurement)) {
                            if ((currentMeasurement.getQuantityLeast() != null) &&
                                    (currentMeasurement.getQuantityMost() != null)) {
                                measurements.add(currentMeasurement);
                                currentMeasurement = new Measurement();
                                openMeasurement = null;
                            }
                        }
                    } else if (openMeasurement == UnitUtilities.Measurement_Type.INTERVAL_BASE_RANGE) {
                        if (isMeasurementValid(currentMeasurement)) {
                            if ((currentMeasurement.getQuantityBase() != null) &&
                                    (currentMeasurement.getQuantityRange() != null)) {
                                measurements.add(currentMeasurement);
                                currentMeasurement = new Measurement();
                                openMeasurement = null;
                            }
                        }
                    }
                    break;
                case QUANTITY_UNIT_RIGHT:
                    System.out.println("unit (right attachment): " + clusterContent);
                    if ((openMeasurement == UnitUtilities.Measurement_Type.VALUE) || (openMeasurement == UnitUtilities.Measurement_Type.CONJUNCTION)) {
                        if (isMeasurementValid(currentMeasurement)) {
                            measurements.add(currentMeasurement);
                            currentMeasurement = new Measurement();
                            //currentUnit = new Unit();
                            openMeasurement = null;
                        }
                    }
                    currentUnit = new Unit();
                    currentUnit.setRawName(clusterContent);
                    pos = adjustStartOffset(text, pos);
                    currentUnit.setOffsetStart(pos);
                    endPos = adjustEndOffset(text, endPos);
                    currentUnit.setOffsetEnd(endPos);
                    break;
                case QUANTITY_OTHER:
                    break;
                default:
                    logger.error("Warning: unexpected label in quantity parser: " + clusterLabel + " for " + clusterContent);
            }
            pos = endPos;
        }

        if (isMeasurementValid(currentMeasurement)) {
            measurements.add(currentMeasurement);
        }

        measurements = MeasurementOperations.postCorrection(measurements);
        return measurements;
    }

    private int adjustEndOffset(String text, int endPos) {
        while (text.charAt(endPos - 1) == ' ') {
            endPos--;
        }
        return endPos;
    }

    private int adjustStartOffset(String text, int pos) {
        while (text.charAt(pos) == ' ') {
            pos++;
        }
        return pos;
    }

    private boolean isMeasurementValid(Measurement currentMeasurement) {
        return ((currentMeasurement.getType() != null) && (
                ((currentMeasurement.getQuantityList() != null) &&
                        (currentMeasurement.getQuantityList().size() > 0)) ||
                        (currentMeasurement.getQuantityAtomic() != null) ||
                        ((currentMeasurement.getQuantityLeast() != null) || (currentMeasurement.getQuantityMost() != null)) ||
                        ((currentMeasurement.getQuantityBase() != null) || (currentMeasurement.getQuantityRange() != null))
        )
        );
    }


}