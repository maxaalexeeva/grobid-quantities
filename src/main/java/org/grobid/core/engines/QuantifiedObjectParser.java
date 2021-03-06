package org.grobid.core.engines;

import org.grobid.core.data.Measurement;
import org.grobid.core.layout.LayoutToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Parser for identifying and attaching the quantified "substance".
 */
public class QuantifiedObjectParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuantifiedObjectParser.class);

    private static volatile QuantifiedObjectParser instance;

    public static QuantifiedObjectParser getInstance() {
        if (instance == null) {
            getNewInstance();
        }
        return instance;
    }

    private static synchronized void getNewInstance() {
        instance = new DefaultQuantifiedObjectParser();
    }

    protected QuantifiedObjectParser() {
        //super(GrobidModels.VALUES);
    }

    /** do nothing for the moment, use DefaultSubstanceParser ;) */
    public List<Measurement> parseSubstance(List<LayoutToken> tokens, List<Measurement> measurements) {
        return measurements;
    }

}