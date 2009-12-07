/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.idgen.processor;

import java.util.ArrayList;
import java.util.List;

import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.IdentifierSource;
import org.openmrs.module.idgen.IdgenUtil;
import org.openmrs.module.idgen.SequentialIdentifierGenerator;
import org.openmrs.module.idgen.service.IdentifierSourceService;

/**
 * Evaluates a SequentialIdentifierSource
 */
public class SequentialIdentifierGeneratorProcessor implements IdentifierSourceProcessor {

	/** 
	 * @see IdentifierSourceProcessor#getIdentifiers(IdentifierSource, int)
	 */
	public List<String> getIdentifiers(IdentifierSource source, int batchSize) {
		
		SequentialIdentifierGenerator seq = (SequentialIdentifierGenerator)source;
		long sequenceValue = seq.getNextSequenceValue();
    	if (sequenceValue < 0) {
    		if (seq.getFirstIdentifierBase() != null) {
    			sequenceValue = IdgenUtil.convertFromBase(seq.getFirstIdentifierBase(), seq.getBaseCharacterSet().toCharArray());
    		}
    		else {
    			sequenceValue = 1;
    		}
    	}

    	List<String> identifiers = new ArrayList<String>();
    	for (int i=0; i<batchSize; i++) {
	    	identifiers.add(seq.getIdentifierForSeed(sequenceValue));
	    	sequenceValue++;
    	}
    	
    	seq.setNextSequenceValue(sequenceValue);
    	Context.getService(IdentifierSourceService.class).saveIdentifierSource(source);
    	
    	return identifiers;
	}
}