/*
 * Copyright 2013 National Bank of Belgium
 * 
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved 
 * by the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 * 
 * http://ec.europa.eu/idabc/eupl
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and 
 * limitations under the Licence.
 */
package ec.nbdemetra.jdbc;

import ec.nbdemetra.ui.Config;
import ec.nbdemetra.ui.interchange.ExportAction;
import ec.nbdemetra.ui.interchange.Exportable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.swing.JMenuItem;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.NodeAction;
import org.openide.util.actions.Presenter;

@ActionID(category = "Edit", id = "ec.nbdemetra.jdbc.ExportJndiJdbcConnection")
@ActionRegistration(displayName = "#CTL_ExportJndiJdbcConnection", lazy = false)
@ActionReferences({
    @ActionReference(path = "Databases/Explorer/Connection/Actions", position = 470)
})
@Messages("CTL_ExportJndiJdbcConnection=Export to")
public final class ExportJndiJdbcConnection extends NodeAction implements Presenter.Popup {

    public ExportJndiJdbcConnection() {
    }

    @Override
    public JMenuItem getPopupPresenter() {
        JMenuItem result = ExportAction.getPopupPresenter(getExportables(getActivatedNodes()));
        result.setText(Bundle.CTL_ExportJndiJdbcConnection());
        return result;
    }

    @Override
    protected void performAction(Node[] activatedNodes) {
    }

    @Override
    protected boolean enable(Node[] activatedNodes) {
        return true;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return null;
    }

    private static List<Exportable> getExportables(Node[] activatedNodes) {
        List<Exportable> result = new ArrayList<>();
        for (final Node o : activatedNodes) {
            result.add(() -> toConfig(getConnectionBean(o)));
        }
        return result;
    }

    private static DriverBasedConfig getConnectionBean(Node activatedNode) {
        return DbExplorerUtil.exportConnection(DbExplorerUtil.findConnection(activatedNode).get());
    }

    @Nonnull
    private static Config toConfig(DriverBasedConfig conn) {
        Config.Builder result = Config.builder(DriverBasedConfig.class.getName(), conn.getDisplayName(), "")
                .put("driverClass", conn.getDriverClass())
                .put("databaseUrl", conn.getDatabaseUrl())
                .put("schema", conn.getSchema());
        for (Map.Entry<String, String> o : conn.getParams().entrySet()) {
            result.put("prop_" + o.getKey(), o.getValue());
        }
        return result.build();
    }
}
