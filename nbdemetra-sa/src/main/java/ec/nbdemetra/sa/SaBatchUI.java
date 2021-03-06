/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ec.nbdemetra.sa;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import ec.nbdemetra.sa.MultiProcessingController.SaProcessingState;
import ec.nbdemetra.ui.ActiveViewManager;
import ec.nbdemetra.ui.DemetraUI;
import ec.nbdemetra.ui.DemetraUiIcon;
import ec.nbdemetra.ui.IActiveView;
import ec.nbdemetra.ui.Menus;
import ec.nbdemetra.ui.Menus.DynamicPopup;
import ec.nbdemetra.ui.MonikerUI;
import ec.nbdemetra.ui.NbComponents;
import ec.nbdemetra.ui.awt.ListTableModel;
import ec.nbdemetra.ui.awt.PopupMenuAdapter;
import ec.nbdemetra.ui.notification.MessageType;
import ec.nbdemetra.ui.notification.NotifyUtil;
import ec.nbdemetra.ws.WorkspaceItem;
import ec.nbdemetra.ws.ui.SpecSelectionComponent;
import ec.satoolkit.ISaSpecification;
import ec.satoolkit.tramoseats.TramoSeatsSpecification;
import ec.satoolkit.x13.X13Specification;
import ec.tss.TsCollection;
import static ec.tss.TsFactory.toTsCollection;
import ec.tss.TsInformationType;
import ec.tss.datatransfer.TransferableXml;
import ec.tss.datatransfer.TssTransferSupport;
import ec.tss.sa.EstimationPolicyType;
import ec.tss.sa.SaItem;
import ec.tss.sa.SaProcessing;
import ec.tss.sa.documents.SaDocument;
import ec.tss.tsproviders.utils.MultiLineNameUtil;
import ec.tss.xml.sa.XmlSaProcessing;
import ec.tstoolkit.algorithm.CompositeResults;
import ec.tstoolkit.algorithm.ProcQuality;
import ec.tstoolkit.data.DescriptiveStatistics;
import ec.ui.view.tsprocessing.DefaultProcessingViewer;
import ec.ui.view.tsprocessing.TsProcessingViewer;
import ec.util.grid.swing.XTable;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.TransferHandler.TransferSupport;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.core.spi.multiview.CloseOperationState;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.MultiViewElementCallback;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.DropDownButtonFactory;
import org.openide.nodes.Node;
import org.openide.util.ImageUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Kristof Bayens
 * @author Philippe Charles
 * @author Mats Maggi
 */
public class SaBatchUI extends AbstractSaProcessingTopComponent implements MultiViewElement, IActiveView {

    private static final String REFRESH_MESSAGE = "Are you sure you want to refresh the data?";
    private static final String REFRESH_LOCAL_MESSAGE = "Are you sure you want to refresh the selected items?";
    private static final String DELETE_LOCAL_MESSAGE = "Are you sure you want to delete the selected items?";
    private static final String PASTE_FAILED_MESSAGE = "Unable to paste data?";

    // MultiViewElement >
    @Override
    public JComponent getVisualRepresentation() {
        return visualRepresentation;
    }

    @Override
    public JComponent getToolbarRepresentation() {
        return toolBarRepresentation;
    }

    @Override
    public void componentOpened() {
        super.componentOpened();
    }

    @Override
    public void componentClosed() {
        controller.dispose();
        stop();
        detail.dispose();
        for (PropertyChangeListener listener : this.getPropertyChangeListeners()) {
            this.removePropertyChangeListener(listener);
        }
        super.componentClosed();
    }

    @Override
    public void componentShowing() {
    }

    @Override
    public void componentHidden() {
    }

    @Override
    public void componentActivated() {
        super.componentActivated();
        ActiveViewManager.getInstance().set(this);
        active = true;
    }

    @Override
    public void componentDeactivated() {
        super.componentDeactivated();
        ActiveViewManager.getInstance().set(null);
        active = false;
    }

    @Override
    public void setMultiViewCallback(MultiViewElementCallback callback) {
    }

    @Override
    public CloseOperationState canCloseElement() {
        return CloseOperationState.STATE_OK;
    }
    // < MultiViewElement

    @Override
    public Node getNode() {
        return null;
    }

    @Override
    public boolean hasContextMenu() {
        return true;
    }

    public int getSelectionCount() {
        return selection.length;
    }

    public enum SaItemPriorityDefinition {

        Log,
        Level
    }
    // CONSTANTS
    private static final Logger LOGGER = LoggerFactory.getLogger(SaBatchUI.class);
    // PROPERTIES DEFINITIONS
    public static final String DEFAULT_SPECIFICATION_PROPERTY = "specificationProperty";
    public static final String PROCESSING_PROPERTY = "processing";
    public static final String SELECTION_PROPERTY = "itemSelection";
    // PROPERTIES
    private ISaSpecification defaultSpecification;
    private SaItem[] selection = new SaItem[0];
    // main components
    private final JSplitPane visualRepresentation;
    private final JToolBar toolBarRepresentation;
    // toolBar stuff
    private final JButton runButton;
    private final JLabel statusLabel;
    private final JLabel itemsLabel;
    private final JLabel defSpecLabel;
    private JToggleButton buttonCollapse;
    // visual stuff
    private final XTable master;
    private final TsProcessingViewer detail;
    // a trier
    private ProgressHandle progressHandle;
    private boolean active;
    private SwingWorker<Void, SaItem> worker;
    private final SaProcessingModel model;
    private final ListTableSelectionListener listTableListener;
    
    private final DeleteActionPanel deleteActionPanel; 

    public SaBatchUI(WorkspaceItem<MultiProcessingDocument> doc, MultiProcessingController controller) {
        super(doc, controller);
        this.defaultSpecification = null;

        setName(doc.getDisplayName());
        toolBarRepresentation = NbComponents.newInnerToolbar();
        toolBarRepresentation.setFloatable(false);
        toolBarRepresentation.addSeparator();
        toolBarRepresentation.add(Box.createRigidArea(new Dimension(5, 0)));
        runButton = toolBarRepresentation.add(new AbstractAction("", DemetraUiIcon.COMPILE_16) {
            @Override
            public void actionPerformed(ActionEvent e) {
                start(false);
            }
        });
        runButton.setDisabledIcon(ImageUtilities.createDisabledIcon(runButton.getIcon()));
        toolBarRepresentation.addSeparator();
        statusLabel = (JLabel) toolBarRepresentation.add(new JLabel());
        toolBarRepresentation.addSeparator();
        itemsLabel = (JLabel) toolBarRepresentation.add(new JLabel());
        toolBarRepresentation.addSeparator();

        JPopupMenu specPopup = new JPopupMenu();
        final JButton specButton = (JButton) toolBarRepresentation.add(DropDownButtonFactory.createDropDownButton(DemetraUiIcon.BLOG_16, specPopup));
        specPopup.add(new SpecSelectionComponent()).addPropertyChangeListener(evt -> {
            String p = evt.getPropertyName();
            if (p.equals(SpecSelectionComponent.SPECIFICATION_PROPERTY) && evt.getNewValue() != null) {
                setDefaultSpecification((ISaSpecification) evt.getNewValue());
            } else if (p.equals(SpecSelectionComponent.ICON_PROPERTY) && evt.getNewValue() != null) {
                specButton.setIcon(ImageUtilities.image2Icon((Image) evt.getNewValue()));
            }
        });
        specPopup.addPopupMenuListener(new PopupMenuAdapter() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                ((SpecSelectionComponent) ((JPopupMenu) e.getSource()).getComponent(0)).setSpecification(getDefaultSpecification());
            }
        });

        defSpecLabel = (JLabel) toolBarRepresentation.add(new JLabel());
        defSpecLabel.setText(defaultSpecification == null ? "" : defaultSpecification.toLongString());
        toolBarRepresentation.add(Box.createHorizontalGlue());
        toolBarRepresentation.addSeparator();
        buttonCollapse = (JToggleButton) toolBarRepresentation.add(new JToggleButton("Specifications"));
        buttonCollapse.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                detail.setSpecificationsVisible(!detail.isSpecificationsVisible());
            }
        });

        model = new SaProcessingModel();
        listTableListener = new ListTableSelectionListener();

        master = buildList();
        detail = new TsProcessingViewer(TsProcessingViewer.Type.APPLY_RESTORE_SAVE);
        detail.setHeaderVisible(false);
        detail.addPropertyChangeListener(DefaultProcessingViewer.BUTTON_SAVE, evt -> {
            save((SaDocument) detail.getDocument());
        });
        detail.addPropertyChangeListener(DefaultProcessingViewer.BUTTON_RESTORE, evt -> {
            if (selection.length > 0) {
                showDetails(selection[0]);
            }
        });
        visualRepresentation = NbComponents.newJSplitPane(JSplitPane.VERTICAL_SPLIT, NbComponents.newJScrollPane(master), detail);
        visualRepresentation.setResizeWeight(.60d);
        visualRepresentation.setOneTouchExpandable(true);

        setLayout(new BorderLayout());
        add(toolBarRepresentation, BorderLayout.NORTH);
        add(visualRepresentation, BorderLayout.CENTER);

        addPropertyChangeListener(evt -> {
            switch (evt.getPropertyName()) {
                case DEFAULT_SPECIFICATION_PROPERTY:
                    onDefaultSpecificationChange();
                    break;
                case PROCESSING_PROPERTY:
                    onProcessingChange();
                    break;
                case SELECTION_PROPERTY:
                    onSelectionChange();
                    break;
            }
        });
        master.addMouseListener(new DynamicPopup(MultiProcessingManager.LOCALPATH));

        deleteActionPanel = new DeleteActionPanel();
    }

    public boolean isTableEmpty() {
        return master.getModel().getRowCount() == 0;
    }

    // EVENT HANDLERS >
    protected void onDefaultSpecificationChange() {
        // do nothing
    }

    public void editDefaultSpecification() {
        SpecSelectionComponent c = new SpecSelectionComponent();
        c.setSpecification(getDefaultSpecification());
        DialogDescriptor dd = c.createDialogDescriptor("Choose active specification");
        if (DialogDisplayer.getDefault().notify(dd) == NotifyDescriptor.OK_OPTION) {
            setDefaultSpecification(c.getSpecification());
        }
    }

    @Override
    public Action[] getActions() {
        return Menus.createActions(super.getActions(), MultiProcessingManager.CONTEXTPATH);
    }

    protected void onProcessingChange() {
        model.fireTableDataChanged();
        String ts = getCurrentProcessing().getMetaData().get(SaProcessing.TIMESTAMP);
        statusLabel.setText(ts != null ? ("Saved:" + ts) : "New processing");
    }

    @Override
    protected void onSaProcessingStateChange() {
        super.onSaProcessingStateChange();
        switch (controller.getState()) {
            case DONE:
                runButton.setEnabled(true);
                makeBusy(false);

                if (progressHandle != null) {
                    progressHandle.finish();
                }
                break;
            case PENDING:
                runButton.setEnabled(true);
                break;
            case STARTED:
                runButton.setEnabled(false);
                progressHandle = ProgressHandle.createHandle(getDocument().getDisplayName(), () -> worker.cancel(true));
                progressHandle.start(getCurrentProcessing().size());
                break;
        }
    }

    protected void onSelectionChange() {
        listTableListener.setEnabled(false);
        master.getSelectionModel().clearSelection();
        for (SaItem o : selection) {
            int i = master.convertRowIndexToView(getCurrentProcessing().indexOf(o));
            master.getSelectionModel().addSelectionInterval(i, i);
        }
        if (selection.length > 0) {
            SaItem item = selection[0];
            if (!item.isProcessed()) {
                // TODO: put this in another thread
                item.process();
                int idx = getCurrentProcessing().indexOf(item);
                model.fireTableRowsUpdated(idx, idx);
            }
            showDetails(item);
        } else {
            showDetails(null);
        }
        listTableListener.setEnabled(true);
    }
    // < EVENT HANDLERS

    // GETTERS/SETTERS >
    public ISaSpecification getDefaultSpecification() {
        return defaultSpecification;
    }

    public void setDefaultSpecification(ISaSpecification defaultSpecification) {
        ISaSpecification old = this.defaultSpecification;
        this.defaultSpecification = defaultSpecification;
        defSpecLabel.setText(defaultSpecification == null ? "" : defaultSpecification.toLongString());
        firePropertyChange(DEFAULT_SPECIFICATION_PROPERTY, old, this.defaultSpecification);
    }

    public void setSelection(SaItem[] selection) {
        SaItem[] old = this.selection;
        this.selection = selection != null ? selection : new SaItem[0];
        firePropertyChange(SELECTION_PROPERTY, old, this.selection);
    }

    public SaItem[] getSelection() {
        return selection.clone();
    }
    // < GETTERS/SETTERS

    public boolean start(boolean local) {
        makeBusy(true);
        worker = new SwingWorkerImpl(local);
        worker.addPropertyChangeListener(evt -> {
            switch (worker.getState()) {
                case DONE:
                    if (progressHandle != null) {
                        progressHandle.finish();
                    }
                    if (controller != null) {
                        controller.setState(SaProcessingState.DONE);
                    }
                    break;
                case PENDING:
                    controller.setState(SaProcessingState.PENDING);
                    break;
                case STARTED:
                    controller.setState(SaProcessingState.STARTED);
                    break;
            }
        });
        worker.execute();
        return true;
    }

    public boolean stop() {
        return worker != null ? worker.cancel(true) : false;
    }

    public void setInitialOrder() {
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(model);
        sorter.setComparator(SaProcessingModel.SERIES, SaItemComparer.Name);
        sorter.setComparator(SaProcessingModel.METHOD, SaItemComparer.Method);
        sorter.setComparator(SaProcessingModel.STATUS, SaItemComparer.Status);
        sorter.setComparator(SaProcessingModel.PRIORITY, SaItemComparer.Priority);
        sorter.setComparator(SaProcessingModel.QUALITY, SaItemComparer.Quality);
        master.setRowSorter(sorter);
    }

    public void refresh(EstimationPolicyType policy, boolean nospan, boolean interactive) {
        if (interactive) {
            NotifyDescriptor nd = new NotifyDescriptor.Confirmation(REFRESH_MESSAGE, NotifyDescriptor.OK_CANCEL_OPTION);
            if (DialogDisplayer.getDefault().notify(nd) != NotifyDescriptor.OK_OPTION) {
                return;
            }
        }
        getDocument().getElement().refresh(policy, nospan);
        start(false);
    }

    public void refreshSelection(EstimationPolicyType policy, boolean nospan, boolean interactive) {
        if (interactive) {
            NotifyDescriptor nd = new NotifyDescriptor.Confirmation(REFRESH_LOCAL_MESSAGE, NotifyDescriptor.OK_CANCEL_OPTION);
            if (DialogDisplayer.getDefault().notify(nd) != NotifyDescriptor.OK_OPTION) {
                return;
            }
        }
        getCurrentProcessing().refresh(Arrays.asList(selection), policy, nospan);
        start(true);
    }

    public void paste(boolean interactive) {
        Transferable dataobj = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
        if (dataobj != null) {
            if (pasteTs(dataobj)) {
                redrawAll();
                return;
            }
            if (pasteSaProcessing(dataobj)) {
                redrawAll();
                return;
            }
        }
        if (interactive) {
            NotifyDescriptor nd = new NotifyDescriptor.Message(PASTE_FAILED_MESSAGE);
            DialogDisplayer.getDefault().notify(nd);
        }
    }

    private boolean pasteTs(Transferable dataobj) {
        TsCollection coll = ec.tss.datatransfer.TssTransferSupport.getDefault().toTsCollection(dataobj);
        if (coll != null) {
            getCurrentProcessing().addRange(defaultSpecification, coll);
            controller.setState(SaProcessingState.READY);
            return true;
        } else {
            return false;
        }
    }

    private boolean pasteSaProcessing(Transferable dataobj) {
        SaProcessing processing = ec.tss.datatransfer.TransferableXml.read(dataobj, SaProcessing.class, XmlSaProcessing.class);
        if (processing != null) {
            getCurrentProcessing().addAll(processing);
            controller.setState(SaProcessingState.READY);
            return true;
        } else {
            return false;
        }
    }

    public void remove(boolean interactive) {
        SaItem[] items = selection;
        if (items == null) {
            return;
        }
        if (interactive) {
            deleteActionPanel.setItems(items); 
            NotifyDescriptor nd = new NotifyDescriptor(deleteActionPanel,
                    "Delete confirmation", 
                    NotifyDescriptor.YES_NO_OPTION, 
                    NotifyDescriptor.QUESTION_MESSAGE, 
                    null, 
                    NotifyDescriptor.YES_OPTION);
            
            if (DialogDisplayer.getDefault().notify(nd) != NotifyDescriptor.YES_OPTION) {
                return;
            }
        }
        
        List<SaItem> itemsToDelete = new ArrayList<>();
        int[] indexToDelete = deleteActionPanel.getSelectedIndices(); 
        for (int index : indexToDelete) { 
            itemsToDelete.add(items[index]); 
        }
        getCurrentProcessing().removeAll(itemsToDelete); 
        
        redrawAll();
        controller.setState(SaProcessingState.READY);
    }

    public void copy() {
        SaItem[] items = selection;
        if (items == null) {
            return;
        }
        List<SaItem> litems = Arrays.asList(items);
        copy(litems);
    }

    public void copy(Collection<SaItem> litems) {
        SaProcessing processing = new SaProcessing();
        processing.addAll(litems);
        TransferableXml transferable = new TransferableXml(processing, XmlSaProcessing.class);
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
    }

    public void copySeries() {
        SaItem[] items = selection;
        if (items == null) {
            return;
        }
        List<SaItem> litems = Arrays.asList(items);
        copySeries(litems);
    }

    public void copySeries(Collection<SaItem> litems) {
        TsCollection tmp = litems.stream()
                .map(SaItem::getTs)
                .collect(toTsCollection());
        Transferable transferable = TssTransferSupport.getDefault().fromTsCollection(tmp);
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
    }

    public void cut() {
        SaItem[] items = selection;
        if (items == null) {
            return;
        }
        List<SaItem> litems = Arrays.asList(items);
        cut(litems);
    }

    public void cut(Collection<SaItem> litems) {
        SaProcessing processing = new SaProcessing();
        processing.addAll(litems);
        TransferableXml transferable = new TransferableXml(processing, XmlSaProcessing.class);
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
        getCurrentProcessing().removeAll(litems);
        redrawAll();
        controller.setState(SaProcessingState.READY);
    }

    private XTable buildList() {
        final XTable result = new XTable();
        result.setModel(model);
        result.setTransferHandler(new SaProcessingTransferHandler());
        result.setDragEnabled(false);
        result.setDropMode(DropMode.ON);
        result.setFillsViewportHeight(true);
        result.setNoDataRenderer(new XTable.DefaultNoDataRenderer("Drop data here", "Drop data here"));

        final ListSelectionModel lsmodel = result.getSelectionModel();
        lsmodel.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        lsmodel.addListSelectionListener(listTableListener);

        XTable.setWidthAsPercentages(result, .35, .1, .1, .1, .05, .15, .05);
        result.getColumnModel().getColumn(SaProcessingModel.SERIES).setCellRenderer(new SeriesRenderer());
        result.getColumnModel().getColumn(SaProcessingModel.METHOD).setCellRenderer(new MethodRenderer());
        result.getColumnModel().getColumn(SaProcessingModel.ESTIMATION).setCellRenderer(new EstimationRenderer());
        result.getColumnModel().getColumn(SaProcessingModel.STATUS).setCellRenderer(new StatusRenderer());
        result.getColumnModel().getColumn(SaProcessingModel.PRIORITY).setCellRenderer(new PriorityRenderer());
        result.getColumnModel().getColumn(SaProcessingModel.QUALITY).setCellRenderer(new QualityRenderer());
        result.getColumnModel().getColumn(SaProcessingModel.WARNINGS).setCellRenderer(new WarningsRenderer());

        TableRowSorter<TableModel> sorter = new TableRowSorter<>(result.getModel());
        sorter.setComparator(SaProcessingModel.SERIES, SaItemComparer.Name);
        sorter.setComparator(SaProcessingModel.METHOD, SaItemComparer.Method);
        sorter.setComparator(SaProcessingModel.STATUS, SaItemComparer.Status);
        sorter.setComparator(SaProcessingModel.PRIORITY, SaItemComparer.Priority);
        sorter.setComparator(SaProcessingModel.QUALITY, SaItemComparer.Quality);
        result.setRowSorter(sorter);

        result.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = result.getSelectedRow();
                if (e.getClickCount() > 1 && row != -1) {
                    SaItem item = model.getValues().get(result.getRowSorter().convertRowIndexToModel(row));
                    DemetraUI.getDefault().getTsAction().open(item.getTs());
                }
            }
        });

        return result;
    }

    private void refreshInfo() {
        String ts = getCurrentProcessing().getMetaData().get(SaProcessing.TIMESTAMP);
        if (!Strings.isNullOrEmpty(ts)) {
            if (getCurrentProcessing().isDirty()) {
                statusLabel.setText("Unsaved");
            } else {
                statusLabel.setText("Saved:" + ts);
            }
        } else {
            statusLabel.setText("New processing");
        }
    }

    public void redrawAll() {
        int n = getCurrentProcessing().size();
        itemsLabel.setText(Integer.toString(n) + (n < 2 ? " item" : " items"));
        model.fireTableDataChanged();
    }

    private void showDetails(SaItem item) {
        if (item == null) {
            detail.setSpecificationsVisible(false);
            buttonCollapse.setSelected(false);
            buttonCollapse.setEnabled(false);
            detail.setDocument(null);
        } else {
            SaDocument<?> doc = (SaDocument<?>) detail.getDocument();
            if (doc == null || !item.fillDocument(doc)) {
                detail.setDocument(item.toDocument());
            } else {
                detail.refreshAll();
            }
            buttonCollapse.setEnabled(true);
            detail.setSpecificationsVisible(buttonCollapse.isSelected());
        }
    }

    void save(SaDocument<?> doc) {
        if (selection.length == 0) {
            return;
        }
        SaItem item = selection[0];
        SaItem nitem = new SaItem(doc.getSpecification().clone(), EstimationPolicyType.Interactive, null, doc.getInput());
        nitem.unsafeFill(doc.getResults());
        selection[0] = nitem;
        getCurrentProcessing().replace(item, nitem);
        int idx = getCurrentProcessing().indexOf(nitem);
        model.fireTableRowsUpdated(idx, idx);
        refreshInfo();
    }

    public void clearPriority(List<SaItem> items) {
        for (SaItem item : items) {
            item.setPriority(0);
        }
        model.fireTableDataChanged();
    }

    public void setPriority(List<SaItem> items, SaItemPriorityDefinition def) {
        if (def == SaItemPriorityDefinition.Level) {
            setLevelPriority(items);
        } else {
            setLogPriority(items);
        }
    }

    public void setLevelPriority(List<SaItem> items) {
        int n = items.size();
        if (n == 0) {
            return;
        }
        double maxavg = 0;
        double[] avg = new double[n];

        int i = 0;
        for (SaItem item : items) {
            if (item.getTsData() != null) {
                DescriptiveStatistics stats = new DescriptiveStatistics(item.getTsData().getValues());
                double cur = stats.getAverage();
                if (cur > maxavg) {
                    maxavg = cur;

                }
                avg[i] = cur;
            }
            ++i;
        }

        i = 0;
        for (SaItem item : items) {
            item.setPriority((int) Math.floor(avg[i++] / maxavg * 10));
        }
        redrawAll();
    }

    public void setLogPriority(List<SaItem> items) {
        int n = items.size();
        if (n == 0) {
            return;
        }
        double maxavg = 0;
        double[] avg = new double[n];

        int i = 0;
        for (SaItem item : items) {
            if (item.getTsData() != null) {
                DescriptiveStatistics stats = new DescriptiveStatistics(item.getTsData().getValues());
                double cur = stats.getAverage();
                if (cur > 0) {
                    cur = Math.log10(cur);

                }
                if (cur < 0) {
                    cur = 0;

                }
                if (cur > maxavg) {
                    maxavg = cur;

                }
                avg[i] = cur;
            }
            ++i;
        }
        if (maxavg == 0) {
            return;

        }
        i = 0;
        for (SaItem item : items) {
            item.setPriority((int) (avg[i++] / maxavg * 10));
        }
        redrawAll();
    }

    public void setPriority(List<SaItem> items, int p) {
        if (items.isEmpty()) {
            return;
        }
        for (SaItem item : items) {
            item.setPriority(p);
        }
        redrawAll();
    }

    private class ListTableSelectionListener implements ListSelectionListener {

        boolean enabled = true;

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (enabled && !e.getValueIsAdjusting()) {
                if (detail.isDirty()) {
                    NotifyDescriptor nd = new NotifyDescriptor.Confirmation("There are unsaved changes in the previously selected item's spec."
                            + "\nDo you want to save them ?", "Unsaved changes", NotifyDescriptor.YES_NO_OPTION);
                    if (DialogDisplayer.getDefault().notify(nd) == NotifyDescriptor.YES_OPTION) {
                        save((SaDocument) detail.getDocument());
                    }
                    detail.setDirty(false);
                }

                setSelection(Arrays.stream(master.getSelectedRows())
                        .mapToObj(i -> getCurrentProcessing().get(master.convertRowIndexToModel(i)))
                        .toArray(SaItem[]::new));
            }
        }
    }

    abstract static class SimpleRenderer<T> extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            label.setText(getText((T) value));
            label.setToolTipText(getToolTipText((T) value));
            label.setIcon(getIcon((T) value));
            if (!isSelected) {
                label.setForeground(getColor((T) value));
            }
            return label;
        }

        protected String getText(T item) {
            return item.toString();
        }

        protected String getToolTipText(T item) {
            return null;
        }

        protected Color getColor(T item) {
            return null;
        }

        protected Icon getIcon(T item) {
            return null;
        }
    }

    static class SeriesRenderer extends SimpleRenderer<SaItem> {

        final MonikerUI monikerUI = MonikerUI.getDefault();

        @Override
        protected String getText(SaItem item) {
            String name = item.getTs().getName();
            return !Strings.isNullOrEmpty(name) ? MultiLineNameUtil.join(name) : ("item_" + item.getKey());
        }

        @Override
        protected String getToolTipText(SaItem item) {
            String name = item.getTs().getName();
            return !Strings.isNullOrEmpty(name) ? MultiLineNameUtil.toHtml(name) : null;
        }

        @Override
        protected Color getColor(SaItem item) {
            return item.getTs().isFrozen() ? Color.gray : null;
        }

        @Override
        public Icon getIcon(SaItem item) {
            return monikerUI.getIcon(item.getTs().getMoniker());
        }
    }

    static class MethodRenderer extends SimpleRenderer<SaItem> {

        @Override
        protected String getText(SaItem item) {
            return item.getDomainSpecification().toString();
        }

        @Override
        protected Color getColor(SaItem item) {
            ISaSpecification spec = item.getDomainSpecification();
            if (spec instanceof TramoSeatsSpecification) {
                return Color.blue;
            } else if (spec instanceof X13Specification) {
                return Color.magenta;
            }
            return null;
        }
    }

    static class EstimationRenderer extends SimpleRenderer<SaItem> {

        @Override
        protected String getText(SaItem item) {
            if (item.getEstimationSpecification() == null) {
                return null;
            } else if (item.getEstimationPolicy() == EstimationPolicyType.FixedParameters) {
                return "Current";
            } else if (item.getEstimationPolicy() == EstimationPolicyType.Complete) {
                return "Concurrent";
            } else {
                return item.getEstimationPolicy().name();
            }
        }
    }

    static class StatusRenderer extends SimpleRenderer<SaItem> {

        @Override
        protected String getText(SaItem item) {
            return item.getStatus().toString();
        }

        @Override
        protected Color getColor(SaItem item) {
            switch (item.getStatus()) {
                case Unprocessed:
                    return Color.GRAY;
                case Pending:
                    return Color.ORANGE;
                case Valid:
                    return null;
                default:
                    return Color.RED;
            }
        }
    }

    static class PriorityRenderer extends SimpleRenderer<SaItem> {

        @Override
        protected String getText(SaItem item) {
            int priority = item.getPriority();
            return priority >= 0 ? Integer.toString(priority) : null;
        }
    }

    static class QualityRenderer extends SimpleRenderer<SaItem> {

        @Override
        protected String getText(SaItem item) {
            return item.getQuality() != ProcQuality.Undefined
                    ? item.getQuality().name() : null;
        }

        @Override
        protected Color getColor(SaItem item) {
            return item.getQuality() != ProcQuality.Undefined
                    ? getColor(item.getQuality()) : null;
        }

        Color getColor(ProcQuality quality) {
            switch (quality) {
                case Error:
                    return Color.RED.darker().darker();
                case Severe:
                    return Color.RED.darker();
                case Bad:
                    return Color.RED;
                case Uncertain:
                    return Color.ORANGE.darker();
                case Good:
                    return Color.GREEN.darker();
                case Accepted:
                    return Color.GRAY;
                case Undefined:
                    return null;
            }
            throw new RuntimeException();
        }
    }

    static class WarningsRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            SaItem item = (SaItem) value;
            if (item.getStatus() == SaItem.Status.Unprocessed) {
                label.setText("");
                return label;
            }
            String[] warnings = item.getWarnings();
            char[] tmp = new char[warnings.length];
            Arrays.fill(tmp, '!');
            label.setText(String.valueOf(tmp));
            label.setToolTipText(Joiner.on(". ").join(warnings));
            return label;
        }
    }

    class SaProcessingModel extends ListTableModel<SaItem> {

        static final int SERIES = 0, METHOD = 1, ESTIMATION = 2, STATUS = 3, PRIORITY = 4, QUALITY = 5, WARNINGS = 6;
        final List<String> columnNames = Arrays.asList("Series", "Method", "Estimation", "Status", "Priority", "Quality", "Warnings");

        @Override
        protected List<String> getColumnNames() {
            return columnNames;
        }

        @Override
        protected List<SaItem> getValues() {
            return getCurrentProcessing();
        }

        @Override
        protected Object getValueAt(SaItem row, int columnIndex) {
            return row;
        }
    }

    private class SwingWorkerImpl extends SwingWorker<Void, SaItem> {

        private final boolean local;

        public SwingWorkerImpl(boolean local) {

            this.local = local && (selection != null && selection.length > 0);
        }

        @Override
        protected Void doInBackground() throws Exception {
            List<Callable<CompositeResults>> tasks = createTasks();
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
                Thread.currentThread().interrupt();
                LOGGER.info("While processing SaItems", ex);
            }

            if (tasks.size() > 0) {
                if (worker != null && !worker.isCancelled()) {
                    NotifyUtil.show("SA Processing done !", "Processed " + tasks.size() + " items in " + stopwatch.stop().toString(), MessageType.SUCCESS, null, null, null);
                }

                if (!active) {
                    requestAttention(false);
                }
            }

            LOGGER.info("Task: {} items in {} by {} executors with priority {}", new Object[]{tasks.size(), stopwatch.stop().toString(), nThread, priority});
            executorService.shutdown();
            return null;

        }

        List<Callable<CompositeResults>> createTasks() {
            SaItem[] items = local ? selection : getCurrentProcessing().toArray();
            if (items != null && items.length > 0) {
                List<Callable<CompositeResults>> result = new ArrayList(items.length);
                for (final SaItem o : items) {
                    result.add(() -> {
                        if (isCancelled()) {
                            return null;
                        }
                        CompositeResults result1 = o.process();
                        publish(o);
                        return result1;
                    });

                }
                return result;
            } else {
                return null;
            }
        }
        int progressCount = 0;

        @Override
        protected void process(List<SaItem> chunks) {
            model.fireTableDataChanged();
            progressCount += chunks.size();
            if (progressHandle != null) {
                if (!chunks.isEmpty()) {
                    progressHandle.progress(chunks.get(chunks.size() - 1).getTs().getName(), progressCount);
                } else {
                    progressHandle.progress(progressCount);
                }
            }
        }
    }

    private class SaProcessingTransferHandler extends TransferHandler {

        @Override
        public boolean canImport(TransferSupport support) {
            boolean result = TssTransferSupport.getDefault().canImport(support.getDataFlavors());
            if (result && support.isDrop()) {
                support.setDropAction(COPY);
            }
            return result;
        }

        @Override
        public boolean importData(TransferSupport support) {
            TsCollection col = TssTransferSupport.getDefault().toTsCollection(support.getTransferable());
            if (col != null) {
                // FIXME: use of TsCollection#query(...) brings bugs in SaItem#process()
                //col.query(TsInformationType.All);
                col.load(TsInformationType.All);
                if (!col.isEmpty()) {
                    getCurrentProcessing().addRange(defaultSpecification, col);
                    redrawAll();
                }
                return true;
            }
            return false;
        }
    }
}
