/*
 * Copyright 2013 National Bank of Belgium
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved 
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
package ec.nbdemetra.anomalydetection.ui;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import ec.nbdemetra.anomalydetection.AnomalyItem;
import ec.nbdemetra.anomalydetection.ControlNode;
import ec.nbdemetra.anomalydetection.report.CheckLastReportAction;
import ec.nbdemetra.ui.ActiveViewManager;
import ec.nbdemetra.ui.DemetraUI;
import ec.nbdemetra.ui.DemetraUiIcon;
import ec.nbdemetra.ui.IActiveView;
import ec.nbdemetra.ui.NbComponents;
import ec.nbdemetra.ui.notification.MessageType;
import ec.nbdemetra.ui.notification.NotifyUtil;
import ec.nbdemetra.ui.properties.OpenIdePropertySheetBeanEditor;
import ec.nbdemetra.ui.tools.ToolsPersistence;
import ec.tss.Ts;
import ec.tss.TsCollection;
import ec.tss.TsFactory;
import ec.tstoolkit.modelling.arima.CheckLast;
import ec.tstoolkit.modelling.arima.tramo.TramoSpecification;
import ec.ui.chart.JTsChart;
import ec.ui.interfaces.ITsCollectionView;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.AbstractAction;
import static javax.swing.Action.NAME;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import static javax.swing.SwingWorker.StateValue.DONE;
import static javax.swing.SwingWorker.StateValue.PENDING;
import static javax.swing.SwingWorker.StateValue.STARTED;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.settings.ConvertAsProperties;
import org.netbeans.core.spi.multiview.CloseOperationState;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.MultiViewElementCallback;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.DropDownButtonFactory;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Top Component for Check Last batch processing
 *
 * @author Mats Maggi
 */
@ConvertAsProperties(dtd = "-//ec.nbdemetra.ui.tools//CheckLastList//EN",
        autostore = false)
@TopComponent.Description(preferredID = "CheckLastBatchComponent",
        //iconBase="SET/PATH/TO/ICON/HERE", 
        persistenceType = TopComponent.PERSISTENCE_ONLY_OPENED)
@TopComponent.Registration(mode = "editor", openAtStartup = false)
@ActionID(category = "Window", id = "ec.nbdemetra.anomalydetection.ui.CheckLastBatchUI")
@ActionReference(path = "Menu/Statistical methods/Anomaly Detection")
@TopComponent.OpenActionRegistration(displayName = "#CTL_CheckLastAction")
@NbBundle.Messages({
    "CTL_CheckLastAction=Check Last",
    "CTL_CheckLastBatchComponent=Check Last Batch Window",
    "HINT_CheckLastBatchComponent=This is a Check Last Batch Window"
})
public class CheckLastBatchUI extends TopComponent implements ExplorerManager.Provider, MultiViewElement, IActiveView {

    private static final Logger LOGGER = LoggerFactory.getLogger(CheckLastBatchUI.class);
    public static final String DEFAULT_SPECIFICATION_PROPERTY = "specificationProperty";
    public static final String COLLECTION_CHANGE = "collectionChange";
    public static final String STATE_PROPERTY = "state";
    public static final String SELECTION_PROPERTY = "itemSelection";
    // Main Components
    private final JSplitPane visualRepresentation;
    private final JSplitPane tsInformation;
    private final JToolBar toolBarRepresentation;
    // ToolBar stuff
    private JButton runButton;
    private JLabel itemsLabel;
    private JLabel defSpecLabel;
    private JButton prefButton;
    private JButton reportButton;
    // Visual Stuff
    private final JTsCheckLastList list;
    private final CheckLastSummary summary;
    private final JTsChart chart;
    // Thread Stuff
    private ProgressHandle progressHandle;
    private SwingWorker<Void, AnomalyItem> worker;
    private boolean active;
    // Properties
    private Node n;
    private final ExplorerManager mgr = new ExplorerManager();

    public CheckLastBatchUI() {
        setName("Check Last Batch");
        list = new JTsCheckLastList();
        summary = new CheckLastSummary();
        toolBarRepresentation = createToolBar();

        list.addPropertyChangeListener(evt -> {
            switch (evt.getPropertyName()) {
                case JTsCheckLastList.COLOR_VALUES:
                    refreshNode();
                    break;
                case COLLECTION_CHANGE:
                    onCollectionChange();
                    break;
                case SELECTION_PROPERTY:
                    onSelectionChange();
                    break;
                case JTsCheckLastList.NB_CHECK_LAST:
                    onNbCheckLastChange();
                    break;
            }
        });

        addPropertyChangeListener(evt -> {
            String p = evt.getPropertyName();
            if (p.equals(STATE_PROPERTY)) {
                onStateChange();
            }
        });

        chart = new JTsChart();
        chart.setTsUpdateMode(ITsCollectionView.TsUpdateMode.None);
        chart.setLegendVisible(false);

        tsInformation = NbComponents.newJSplitPane(JSplitPane.VERTICAL_SPLIT, summary, chart);
        tsInformation.setResizeWeight(0.5);

        visualRepresentation = NbComponents.newJSplitPane(JSplitPane.HORIZONTAL_SPLIT, list, tsInformation);
        visualRepresentation.setResizeWeight(0.6);

        setLayout(new BorderLayout());
        add(toolBarRepresentation, BorderLayout.NORTH);
        add(visualRepresentation, BorderLayout.CENTER);

        refreshNode();
        associateLookup(ExplorerUtils.createLookup(mgr, getActionMap()));
    }

    private void refreshNode() {
        n = ControlNode.onComponentOpened(mgr, list);

        try {
            mgr.setSelectedNodes(new Node[]{n});
        } catch (PropertyVetoException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    // <editor-fold defaultstate="collapsed" desc="Create Tool Bar">
    private JToolBar createToolBar() {
        JToolBar toolBar = NbComponents.newInnerToolbar();
        toolBar.setFloatable(false);
        toolBar.addSeparator();
        toolBar.add(Box.createRigidArea(new Dimension(5, 0)));
        runButton = toolBar.add(new AbstractAction("", DemetraUiIcon.COMPILE_16) {
            @Override
            public void actionPerformed(ActionEvent e) {
                start(false);
            }
        });
        runButton.setDisabledIcon(ImageUtilities.createDisabledIcon(runButton.getIcon()));
        runButton.setToolTipText("Run the Check Last processing");
        runButton.setEnabled(false);
        toolBar.addSeparator();
        itemsLabel = (JLabel) toolBar.add(new JLabel("No items"));
        toolBar.addSeparator();

        toolBar.add(createLastButton(list));
        toolBar.addSeparator();

        prefButton = toolBar.add(new AbstractAction("", DemetraUiIcon.PREFERENCES) {
            @Override
            public void actionPerformed(ActionEvent e) {
                OpenIdePropertySheetBeanEditor.editNode(n, "Properties", null);
            }
        });
        prefButton.setToolTipText("Open the properties dialog");
        toolBar.addSeparator();

        toolBar.add(createSpecDropDownButton(list));
        defSpecLabel = (JLabel) toolBar.add(new JLabel(list.getSpec() == null ? "" : list.getSpec().toLongString()));

        toolBar.add(Box.createHorizontalGlue());
        toolBar.addSeparator();

        reportButton = toolBar.add(new AbstractAction("", DemetraUiIcon.DOCUMENT_16) {
            @Override
            public void actionPerformed(ActionEvent e) {
                generateReport();
            }
        });
        reportButton.setText("Generate Report");
        reportButton.setHorizontalTextPosition(SwingConstants.RIGHT);
        reportButton.setToolTipText("Generate report of the check last processing");
        reportButton.setEnabled(false);

        return toolBar;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Create Spec Change Button">
    private JButton createSpecDropDownButton(final JTsCheckLastList view) {
        final JPopupMenu addPopup = new JPopupMenu();
        TramoSpecification[] specs = TramoSpecification.allSpecifications();
        for (int i = 0; i < specs.length; ++i) {
            String name = specs[i].toString();
            JCheckBoxMenuItem menuItem = new JCheckBoxMenuItem(name);
            menuItem.setName(name);
            menuItem.addActionListener(new AbstractAction(name) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    TramoSpecification s = TramoSpecification.TR0.clone();
                    JCheckBoxMenuItem source = (JCheckBoxMenuItem) e.getSource();
                    try {
                        s = (TramoSpecification) TramoSpecification.class.getDeclaredField(source.getText()).get(new TramoSpecification());
                    } catch (IllegalAccessException | NoSuchFieldException | SecurityException ex) {
                        LOGGER.error("Tramo Specification " + source.getText() + " can't be accessed !");
                    }
                    view.setSpec(s);
                }
            });
            menuItem.setState(i == specs.length - 1);
            menuItem.setEnabled(i != specs.length - 1);
            addPopup.add(menuItem);
        }
        view.addPropertyChangeListener(JTsCheckLastList.SPEC_CHANGE, evt -> {
            refreshNode();
            for (Component o : addPopup.getComponents()) {
                JCheckBoxMenuItem item = (JCheckBoxMenuItem) o;
                item.setState(view.getSpec().toString().equals(o.getName()));
                item.setEnabled(!item.isSelected());
                defSpecLabel.setText(view.getSpec().toLongString());
                reportButton.setEnabled(false);
            }
        });

        JButton result = DropDownButtonFactory.createDropDownButton(DemetraUiIcon.BLOG_16, addPopup);
        result.setToolTipText("Specification used for the Check Last processing");
        return result;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Create Number of Check Last Button">
    private JButton createLastButton(final JTsCheckLastList view) {
        final JPopupMenu addPopup = new JPopupMenu();
        for (int i = 1; i < 4; ++i) {
            JCheckBoxMenuItem menuItem = new JCheckBoxMenuItem(Integer.toString(i));
            menuItem.setName(Integer.toString(i));
            menuItem.addActionListener(new AbstractAction(Integer.toString(i)) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    view.setLastChecks(Integer.parseInt(getValue(NAME).toString()));
                }
            });
            menuItem.setState(i == 1);
            menuItem.setEnabled(i != 1);
            addPopup.add(menuItem);
        }
        view.addPropertyChangeListener(JTsCheckLastList.NB_CHECK_LAST, evt -> {
            refreshNode();
            for (Component o : addPopup.getComponents()) {
                JCheckBoxMenuItem item = (JCheckBoxMenuItem) o;
                item.setState(view.getLastChecks() == Integer.parseInt(item.getName()));
                item.setEnabled(!item.isSelected());
            }
        });

        JButton result = DropDownButtonFactory.createDropDownButton(DemetraUiIcon.NB_CHECK_LAST_16, addPopup);
        result.setToolTipText("Number of last observations to check");
        return result;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Component States">
    @Override
    public void componentOpened() {
        tsInformation.setDividerLocation((int) (((double) visualRepresentation.getPreferredSize().height) * 0.8));
    }

    @Override
    public void componentClosed() {
        mgr.setRootContext(Node.EMPTY);
        stop();
        list.dispose();
    }

    @Override
    public void componentDeactivated() {
        super.componentDeactivated();
        ActiveViewManager.getInstance().set(null);
        active = false;
    }

    @Override
    public void componentActivated() {
        super.componentActivated();
        ActiveViewManager.getInstance().set(this);
        active = true;
    }

    @Override
    public void componentHidden() {
        super.componentHidden();
    }

    @Override
    public void componentShowing() {
        super.componentShowing();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Getters / Setters">
    @Override
    public Node getNode() {
        return n;
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return mgr;
    }

    @Override
    public boolean fill(JMenu menu) {
        return false;
    }

    @Override
    public boolean hasContextMenu() {
        return false;
    }

    @Override
    public JComponent getVisualRepresentation() {
        return visualRepresentation;
    }

    @Override
    public JComponent getToolbarRepresentation() {
        return toolBarRepresentation;
    }

    @Override
    public void setMultiViewCallback(MultiViewElementCallback callback) {
    }

    @Override
    public CloseOperationState canCloseElement() {
        return CloseOperationState.STATE_OK;
    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_NEVER;
    }
    // </editor-fold>

    private void generateReport() {
        CheckLastReportAction.process(list.getItems2(), list.getReportParameters());
    }

    // <editor-fold defaultstate="collapsed" desc="Thread stuff">
    private class SwingWorkerImpl extends SwingWorker<Void, AnomalyItem> {

        private int progressCount = 0;

        @Override
        protected Void doInBackground() throws Exception {
            List<Callable<Void>> tasks = createTasks();
            if (tasks == null) {
                return null;
            }

            DemetraUI config = DemetraUI.getDefault();
            int nThread = config.getBatchPoolSize().intValue();
            int priority = config.getBatchPriority().intValue();

            ExecutorService executorService = Executors.newFixedThreadPool(nThread, new ThreadFactoryBuilder().setDaemon(true).setPriority(priority).build());
            Stopwatch stopwatch = Stopwatch.createStarted();
            try {
                executorService.invokeAll(tasks);
            } catch (InterruptedException ex) {
                LOGGER.info("Check Last interrupted while processing items");
            }

            if (tasks.size() > 0) {
                NotifyUtil.show("Check Last done !", "Processed " + tasks.size() + " items in " + stopwatch.stop().toString(), MessageType.SUCCESS, null, null, null);
                if (!active) {
                    requestAttention(false);
                }
            }

            LOGGER.info("Task: {} items in {} by {} executors with priority {}", new Object[]{tasks.size(), stopwatch.stop().toString(), nThread, priority});

            executorService.shutdown();

            return null;
        }

        List<Callable<Void>> createTasks() {
            if (list.getItems2() != null && list.getItems2().size() > 0) {
                List<Callable<Void>> result = new ArrayList(list.getItems2().size());
                for (final AnomalyItem o : list.getItems2()) {
                    if (o.getTsData() != null) {
                        if (!o.isProcessed()) {
                            result.add(() -> {
                                if (isCancelled()) {
                                    return null;
                                }
                                CheckLast c = new CheckLast(list.getSpec().build());
                                c.setBackCount(list.getLastChecks());
                                o.process(c);
                                publish(o);
                                list.put(o.getTs().getName(), o);
                                return null;
                            });
                        }
                    }
                }
                return result;
            } else {
                return null;
            }
        }

        @Override
        protected void process(List<AnomalyItem> chunks) {
            list.fireTableDataChanged();
            progressCount += chunks.size();
            if (progressHandle != null) {
                if (!chunks.isEmpty()) {
                    progressHandle.progress(100 * progressCount / list.getItems2().size());
                }
            }
        }
    }

    protected void onStateChange() {
        switch (getState()) {
            case DONE:
                runButton.setEnabled(true);
                if (!list.getItems2().isEmpty()) {
                    reportButton.setEnabled(true);
                }
                makeBusy(false);

                if (progressHandle != null) {
                    progressHandle.finish();
                }
                break;
            case PENDING:
                runButton.setEnabled(true);
                reportButton.setEnabled(false);
                break;
            case STARTED:
                runButton.setEnabled(false);
                reportButton.setEnabled(false);
                progressHandle = ProgressHandle.createHandle("Processing Check Last...", () -> worker.cancel(true));
                progressHandle.start(100);
                break;
        }
    }

    public boolean start(boolean local) {
        makeBusy(true);
        worker = new SwingWorkerImpl();
        worker.addPropertyChangeListener(evt -> {
            firePropertyChange(STATE_PROPERTY, null, worker.getState());
        });
        worker.execute();
        return true;
    }

    public SwingWorker.StateValue getState() {
        return worker != null ? worker.getState() : SwingWorker.StateValue.PENDING;
    }

    public boolean stop() {
        return worker != null ? worker.cancel(true) : false;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Event Handlers">
    private void onSelectionChange() {
        Ts[] ts = list.getSelection();
        if (ts == null || ts.length != 1) {
            summary.set(null, null);
            chart.setTsCollection(null);
        } else {
            AnomalyItem a = list.getMap().get(ts[0].getName());
            if (a.isInvalid() || a.isNotProcessable()) {
                summary.set(null, null);
            } else if (a.getTsData() != null) {
                CheckLast cl = new CheckLast(list.getSpec().build());
                cl.setBackCount(list.getLastChecks());
                cl.check(a.getTsData());
                summary.set(a, cl.getEstimatedModel());
            }

            TsCollection col = TsFactory.instance.createTsCollection();
            col.quietAdd(a.getTs());
            chart.setTsCollection(col);
        }
        summary.repaint();
    }

    private void onCollectionChange() {
        int nbElements = list.getItems2() != null ? list.getItems2().size() : 0;
        itemsLabel.setText(nbElements == 0 ? "No items" : nbElements + (nbElements < 2 ? " item" : " items"));
        summary.set(null, list.getCheckLast().getEstimatedModel());
        summary.repaint();

        runButton.setEnabled(nbElements != 0);

        reportButton.setEnabled(false);
    }

    private void onNbCheckLastChange() {
        reportButton.setEnabled(false);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Properties I/O">
    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
        ToolsPersistence.writeTsCollection(list, p);
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
        ToolsPersistence.readTsCollection(list, p);
    }
    // </editor-fold>
}
