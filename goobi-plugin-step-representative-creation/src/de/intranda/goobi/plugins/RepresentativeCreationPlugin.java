package de.intranda.goobi.plugins;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import net.xeoh.plugins.base.annotations.PluginImplementation;

import org.goobi.beans.Step;
import org.goobi.beans.Process;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.plugin.interfaces.IStepPlugin;

import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.dl.Reference;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.WriteException;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.extern.log4j.Log4j;

@PluginImplementation
@Log4j
public class RepresentativeCreationPlugin implements IStepPlugin, IPlugin {

    private static final String PLUGIN_NAME = "RepresentativeCreation";

    private static final String TITLE_PAGE_NAME = "TitlePage";
    private static final String PAGE_NO_NAME = "physPageNumber";
    private static final String REPRESENTATIVE_NAME = "_representative";

    private Process process;
    private String returnPath;
    private Prefs prefs;

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String getTitle() {
        return PLUGIN_NAME;
    }

    public String getDescription() {
        return PLUGIN_NAME;
    }

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.process = step.getProzess();
        prefs = process.getRegelsatz().getPreferences();
    }

    @Override
    public boolean execute() {

        try {
            Fileformat fileformat = process.readMetadataFile();

            DigitalDocument dd = fileformat.getDigitalDocument();
            DocStruct physical = dd.getPhysicalDocStruct();
            DocStruct logical = dd.getLogicalDocStruct();

            if (physical.getAllMetadata() != null && physical.getAllMetadata().size() > 0) {
                for (Metadata md : physical.getAllMetadata()) {
                    if (md.getType().getName().equals(REPRESENTATIVE_NAME)) {
                        try {
                            Integer value = new Integer(md.getValue());
                            if (log.isTraceEnabled()) {
                                log.trace("Found representative image with order " + value);
                            }
                            return true;
                        } catch (Exception e) {

                        }
                    }
                }
            }

            if (logical.getType().isAnchor()) {
                logical = logical.getAllChildren().get(0);
            }
            if (logical.getAllChildren() == null || logical.getAllChildren().isEmpty()) {
                return true;
            }
            DocStruct titlePage = null;
            for (DocStruct currentDocStruct : logical.getAllChildren()) {
                if (currentDocStruct.getType().getName().equals(TITLE_PAGE_NAME)) {
                    titlePage = currentDocStruct;
                    break;
                }
            }

            if (titlePage == null) {
                if (log.isDebugEnabled()) {
                    log.debug("Found no title page in process " + process.getTitel());
                }
                return true;
            }

            List<Reference> linkedImages = titlePage.getAllToReferences();

            if (linkedImages == null || linkedImages.isEmpty()) {
                return true;
            }
            DocStruct page = linkedImages.get(0).getTarget();
            MetadataType no = prefs.getMetadataTypeByName(PAGE_NO_NAME);
            List<? extends Metadata> pageNoList = page.getAllMetadataByType(no);
            for (Metadata pageNo : pageNoList) {
                if (log.isDebugEnabled()) {
                    log.debug("First image of title page has order number " + pageNo.getValue());
                }
                MetadataType rep = prefs.getMetadataTypeByName(REPRESENTATIVE_NAME);

                Metadata md = new Metadata(rep);
                md.setValue(pageNo.getValue());
                physical.addMetadata(md);
                break;
            }
            if (log.isDebugEnabled()) {
                log.debug("Save file with representative image.");
            }
            process.writeMetadataFile(fileformat);

        } catch (ReadException | PreferencesException | WriteException | IOException | InterruptedException | SwapException | DAOException
                | MetadataTypeNotAllowedException e) {
            log.error(e);
            return false;
        }

        return true;
    }

    @Override
    public String cancel() {
        return returnPath;
    }

    @Override
    public String finish() {
        return returnPath;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public Step getStep() {
        return null;
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return null;
    }

}
