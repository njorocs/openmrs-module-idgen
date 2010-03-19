package org.openmrs.module.idgen.web.controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.PatientIdentifierType;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.IdentifierPool;
import org.openmrs.module.idgen.IdentifierSource;
import org.openmrs.module.idgen.IdgenUtil;
import org.openmrs.module.idgen.LogEntry;
import org.openmrs.module.idgen.PooledIdentifier;
import org.openmrs.module.idgen.RemoteIdentifierSource;
import org.openmrs.module.idgen.SequentialIdentifierGenerator;
import org.openmrs.module.idgen.propertyeditor.IdentifierSourceEditor;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.idgen.validator.IdentifierSourceValidator;
import org.openmrs.module.idgen.validator.RemoteIdentifierSourceValidator;
import org.openmrs.module.idgen.validator.SequentialIdentifierGeneratorValidator;
import org.openmrs.propertyeditor.PatientIdentifierTypeEditor;
import org.openmrs.util.OpenmrsClassLoader;
import org.openmrs.web.WebConstants;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

@Controller
@SessionAttributes("source")
public class IdentifierSourceController {

	protected static Log log = LogFactory.getLog(IdentifierSourceController.class);
	
	//***** CONSTRUCTORS *****
	
	/**
	 * Default Constructor
	 */
	public IdentifierSourceController() { }
	
	//***** INSTANCE METHODS *****
	
	@InitBinder
	public void initBinder(HttpServletRequest request, ServletRequestDataBinder binder) throws Exception {
		binder.registerCustomEditor(PatientIdentifierType.class, new PatientIdentifierTypeEditor());
		binder.registerCustomEditor(IdentifierSource.class, new IdentifierSourceEditor());
	}
	
    /**
     * Edit a new or existing IdentifierSource
     */
    @RequestMapping("/module/idgen/editIdentifierSource")
    public void editIdentifierSource(ModelMap model, HttpServletRequest request,
    							     @RequestParam(required=false, value="source") IdentifierSource source,
    							     @RequestParam(required=false, value="identifierType") PatientIdentifierType identifierType,
    							     @RequestParam(required=false, value="sourceType") String sourceType) {
    	
		if (Context.isAuthenticated()) {
			
			Thread.currentThread().setContextClassLoader(OpenmrsClassLoader.getInstance());
			if (source == null) {
				try {
					Class<?> idSourceType = Context.loadClass(sourceType);
					model.addAttribute("sourceType", sourceType);
					source = (IdentifierSource)idSourceType.newInstance();
					source.setIdentifierType(identifierType);
				}
				catch (Exception e) {
					throw new RuntimeException("Unable to instantiate class " + sourceType, e);
				}
			}
			model.addAttribute("source", source);
			
			List<IdentifierSource> otherCompatibleSources = new ArrayList<IdentifierSource>();
			for (IdentifierSource s : Context.getService(IdentifierSourceService.class).getAllIdentifierSources(false)) {
				if (!s.equals(source) && s.getIdentifierType().equals(source.getIdentifierType())) {
					otherCompatibleSources.add(s);			
				}
			}
			model.addAttribute("otherCompatibleSources", otherCompatibleSources);
		}
    }
    
    /**
     * Retrieves all IdentifierSources
     */
    @RequestMapping("/module/idgen/manageIdentifierSources")
    public void manageIdentifierSources(ModelMap model, 
    									@RequestParam(required=false, value="includeRetired") Boolean includeRetired) {
		if (Context.isAuthenticated()) {
			IdentifierSourceService iss = Context.getService(IdentifierSourceService.class);
			boolean ret = includeRetired == Boolean.TRUE;
			
			Map<PatientIdentifierType, List<IdentifierSource>> sourcesByType = iss.getIdentifierSourcesByType(ret);
			
			List<PatientIdentifierType> identiferTypes = new ArrayList<PatientIdentifierType>();
			for (Iterator<PatientIdentifierType> i = sourcesByType.keySet().iterator(); i.hasNext();) {
				PatientIdentifierType pit = i.next();
				if (sourcesByType.get(pit).isEmpty()) {
					i.remove();
				}
				identiferTypes.add(pit);
			}
			model.addAttribute("sourcesByType", sourcesByType);
			model.addAttribute("identiferTypes", identiferTypes);
			model.addAttribute("sourceTypes", iss.getIdentifierSourceTypes());
		}
    }
    
    /**
     * Deletes an IdentifierSource
     */
    @RequestMapping("/module/idgen/deleteIdentifierSource")
    public String deletePatientSearch(ModelMap model, @RequestParam(required=true, value="source") IdentifierSource source) {
    	Context.getService(IdentifierSourceService.class).purgeIdentifierSource(source);
    	return "redirect:/module/idgen/manageIdentifierSources.list";
    }
    
    /**
     * Saves an IdentifierSource
     */
    @RequestMapping("/module/idgen/saveIdentifierSource")
    public ModelAndView saveIdentifierSource(@ModelAttribute("source") IdentifierSource source, BindingResult result, SessionStatus status) {
		
    	// Validate input
    	Validator v = new IdentifierSourceValidator();
    	if (source instanceof SequentialIdentifierGenerator) {
    		v = new SequentialIdentifierGeneratorValidator();
    	}
    	else if (source instanceof RemoteIdentifierSource) {
    		v = new RemoteIdentifierSourceValidator();
    	}
    	v.validate(source, result);
    	
		if (result.hasErrors()) {
			return new ModelAndView("/module/idgen/editIdentifierSource");
		}
		
		// add/update the flag
		Context.getService(IdentifierSourceService.class).saveIdentifierSource(source);
		
		// clears the command object from the session
		status.setComplete();
		
		// just display the edit page again
		return new ModelAndView("redirect:/module/idgen/manageIdentifierSources.form");
	}
    
    /**
     * Generate Identifiers Page
     */
    @RequestMapping("/module/idgen/viewIdentifierSource")
    public void viewIdentifierSource(ModelMap model, @RequestParam(required=true, value="source") IdentifierSource source) {
    	model.addAttribute("source", source);
    }
    
    /**
     * Generate and Output a Single new Identifier
     */
    @RequestMapping("/module/idgen/generateIdentifier")
    public void generateIdentifier(ModelMap model, HttpServletRequest request, HttpServletResponse response,
    							   @RequestParam(required=true, value="source") IdentifierSource source,
    							   @RequestParam(required=false, value="comment") String comment) throws Exception {
    	exportIdentifiers(model, request, response, source, 1, comment);
    }
    
    /**
     * Export Identifiers To File
     */
    @RequestMapping("/module/idgen/exportIdentifiers")
    public void exportIdentifiers(ModelMap model, HttpServletRequest request, HttpServletResponse response,
    							   @RequestParam(required=true, value="source") IdentifierSource source,
    							   @RequestParam(required=true, value="numberToGenerate") Integer numberToGenerate,
    							   @RequestParam(required=false, value="comment") String comment) throws Exception {
    	
    	IdentifierSourceService iss = Context.getService(IdentifierSourceService.class);
    	
		response.setHeader("Content-Disposition", "attachment; filename=identifiers.txt");
		response.setHeader("Pragma", "no-cache");
    	response.setContentType("text/plain");
    	ServletOutputStream out = response.getOutputStream();
    	String separator = System.getProperty("line.separator");
    	
    	if (StringUtils.isEmpty(comment)) {
    		comment = "Batch Export of " + numberToGenerate + " to file";
    	}
    	
    	List<String> batch = iss.generateIdentifiers(source, numberToGenerate, comment);
    	for (Iterator<String> i = batch.iterator(); i.hasNext();) {
    		String identifier = i.next();
    		out.print(identifier + (i.hasNext() ? separator : ""));
    	}
    }
    
    /**
     * Upload Identifiers From File
     */
    @RequestMapping("/module/idgen/addIdentifiersFromFile")
    public String addIdentifiersFromFile(ModelMap model, HttpServletRequest request, HttpServletResponse response,
    							   @RequestParam(required=true, value="source") IdentifierSource source,
    							   @RequestParam(required=true, value="inputFile") MultipartFile inputFile) throws Exception {
    	
    	IdentifierPool pool = (IdentifierPool)source;
    	List<PooledIdentifier> ids = new ArrayList<PooledIdentifier>();
    	BufferedReader r = null;
    	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    	try {
    		r = new BufferedReader(new InputStreamReader(inputFile.getInputStream()));
    		for (String s = r.readLine(); s != null; s = r.readLine()) {
    		    //HACK: split the string and look for a date:  yyyy-MM-dd, with separator ','
    		    String[] splitStr = s.split(",");
    		    if (splitStr.length == 2){
    		        try {
    		            Date date = sdf.parse(splitStr[1]);
    		            ids.add(new PooledIdentifier(pool, splitStr[0], date, null));
    		        } catch (Exception ex) {
    		            throw new RuntimeException("Unable to convert string " + splitStr[1] + " to date, using standard format yyyy-MM-dd ");
    		        }
    		    } else {
    		        ids.add(new PooledIdentifier(pool, s, null, null));
    		    }
    		    
    		}
    	}
    	finally {
    		if (r != null) {
    			r.close();
    		}
    	}
		IdgenUtil.saveAndCascadeIdsToPoolandSequentialPoolSource(pool, ids);
		return "redirect:/module/idgen/viewIdentifierSource.form?source="+source.getId();
    }
    
    /**
     * Upload Identifiers to Pool From Source
     */
    @RequestMapping("/module/idgen/addIdentifiersFromSource")
    public String addIdentifiersFromSource(ModelMap model, HttpServletRequest request, HttpServletResponse response,
    							   @RequestParam(required=true, value="source") IdentifierSource source,
    							   @RequestParam(required=true, value="batchSize") Integer batchSize) throws Exception {
    	
    	IdentifierPool pool = (IdentifierPool)source;
		Context.getService(IdentifierSourceService.class).addIdentifiersToPool(pool, batchSize);
		return "redirect:/module/idgen/viewIdentifierSource.form?source="+source.getId();
    }
    
    @RequestMapping("/module/idgen/blockPatientIdentifiers")
    public String addIdentifiersFromExistingPatients(ModelMap model, HttpServletRequest request, HttpServletResponse response,
                                   @RequestParam(required=true, value="source") IdentifierSource source) throws Exception {
        
        Set<String> inUseIdentifiers = Context.getService(IdentifierSourceService.class).getPatientIdentifiersByIdentifierType(source.getIdentifierType());
        if (source instanceof IdentifierPool){
            IdentifierPool pool = (IdentifierPool) source;
            List<PooledIdentifier> piList = new ArrayList<PooledIdentifier>();
            for (String st : inUseIdentifiers){
                piList.add(new PooledIdentifier(pool, st, new Date(), null));
            }
            IdgenUtil.saveAndCascadeIdsToPoolandSequentialPoolSource(pool, piList);
        } else if (source instanceof SequentialIdentifierGenerator){
            for (String pi : inUseIdentifiers)
                Context.getService(IdentifierSourceService.class).saveLogEntry(new LogEntry(source, pi, new Date(), Context.getAuthenticatedUser(), null));
        }
        HttpSession httpSession = request.getSession();
        httpSession.setAttribute(WebConstants.OPENMRS_MSG_ATTR, "Success: Identifiers marked as already used by idgen.");
        return "redirect:/module/idgen/viewIdentifierSource.form?source="+source.getId();
    }
}
