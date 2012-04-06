package org.shareables.server;

import org.shareables.models.ModelFactory;
import org.shareables.models.ShareableModel;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Niklas Schnelle
 */
public class ModelRegistry {
    private Map<String, ModelFactory> modelFacs;

    public ModelRegistry(){
        modelFacs = new HashMap<String, ModelFactory>();
    }


    public ShareableModel getModel(String modelName) {
        ModelFactory fac = modelFacs.get(modelName);
        ShareableModel model = null;
        if(fac != null){
            model = fac.createModel();
        }
        return model;
    }

    public void addModel(ModelFactory modelFac){
        modelFacs.put(modelFac.getName(), modelFac);
    }
}
