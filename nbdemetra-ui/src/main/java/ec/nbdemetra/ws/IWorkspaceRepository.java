/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ec.nbdemetra.ws;

import ec.tss.tsproviders.DataSource;
import ec.tstoolkit.design.ServiceDefinition;
import java.util.Collection;

/**
 *
 * @author Jean Palate
 */
@ServiceDefinition
public interface IWorkspaceRepository {
    
    Collection<Class> getSupportedTypes();
    
    void initialize();

    String getName();

    Object getProperties();

    void setProperties();

    Workspace open();

    boolean saveAs(Workspace ws);
    
    boolean load(Workspace ws);

    boolean save(Workspace ws, boolean force);

    boolean delete(Workspace ws);
    
    <D> boolean canHandleItem(Class<D> dclass);

    boolean loadItem(WorkspaceItem<?> item);

    boolean saveItem(WorkspaceItem<?> item);

    boolean deleteItem(WorkspaceItem<?> item);

    void close(Workspace ws_);
    
    DataSource getDefaultDataSource();
}
