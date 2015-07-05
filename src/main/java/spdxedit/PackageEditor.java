package spdxedit;

import javafx.beans.property.ReadOnlyStringWrapper;
import com.google.common.collect.Ordering;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.apache.commons.lang3.ArrayUtils;
import org.controlsfx.control.CheckListView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.rdfparser.InvalidSPDXAnalysisException;
import org.spdx.rdfparser.model.Relationship;
import org.spdx.rdfparser.model.Relationship.RelationshipType;
import org.spdx.rdfparser.model.SpdxFile;
import org.spdx.rdfparser.model.SpdxFile.FileType;
import org.spdx.rdfparser.model.SpdxPackage;
import spdxedit.util.StringableWrapper;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PackageEditor {

    private static final Logger logger = LoggerFactory.getLogger(PackageEditor.class);

    @FXML
    private TitledPane tabFiles;

    @FXML
    private TreeTableView<SpdxFile> filesTable;

    @FXML
    private TreeTableColumn<SpdxFile, String> tblColumnFile;

    @FXML
    private TitledPane tabRelationships;

    @FXML
    private Button btnOk;

    /*** FILE INFORMATION REPRESENTATIONS ***/

    @FXML
    private CheckListView<StringableWrapper<FileType>> chkListFileTypes;

    @FXML
    private CheckBox chkDataFile;

    @FXML
    private CheckBox chkTestCase;

    @FXML
    private CheckBox chkDocumentation;

    @FXML
    private CheckBox chkOptionalComponent;

    @FXML
    private CheckBox chkMetafile;

    @FXML
    private CheckBox chkBuildTool;


    //The package being edited
    private SpdxPackage pkg;

    //The file currently being edited
    private SpdxFile currentFile;


    @FXML
    private void initialize() {
        assert tabFiles != null : "fx:id=\"tabFiles\" was not injected: check your FXML file 'PackageEditor.fxml'.";
        assert filesTable != null : "fx:id=\"filesTable\" was not injected: check your FXML file 'PackageEditor.fxml'.";
        assert tblColumnFile != null : "fx:id=\"tblColumnFile\" was not injected: check your FXML file 'PackageEditor.fxml'.";
        assert chkListFileTypes != null : "fx:id=\"chkListFileTypes\" was not injected: check your FXML file 'PackageEditor.fxml'.";
        assert tabRelationships != null : "fx:id=\"tabRelationships\" was not injected: check your FXML file 'PackageEditor.fxml'.";
        assert btnOk != null : "fx:id=\"btnOk\" was not injected: check your FXML file 'PackageEditor.fxml'.";

        //File relationship checkboxes
        assert chkDataFile != null : "fx:id=\"chkDataFile\" was not injected: check your FXML file 'PackageEditor.fxml'.";
        assert chkTestCase != null : "fx:id=\"chkTestCase\" was not injected: check your FXML file 'PackageEditor.fxml'.";
        assert chkDocumentation != null : "fx:id=\"chkDocumentation\" was not injected: check your FXML file 'PackageEditor.fxml'.";
        assert chkOptionalComponent != null : "fx:id=\"chkOptionalComponent\" was not injected: check your FXML file 'PackageEditor.fxml'.";
        assert chkMetafile != null : "fx:id=\"chkMetafile\" was not injected: check your FXML file 'PackageEditor.fxml'.";
        assert chkBuildTool != null : "fx:id=\"chkBuildTool\" was not injected: check your FXML file 'PackageEditor.fxml'.";

        //Initialise relationship checkbox handling
        //TODO: Could make this easier by extending the CheckBox control?
        chkDataFile.selectedProperty().addListener((observable, oldValue, newValue) -> addOrRemoveRelationshipToPackage(RelationshipType.relationshipType_dataFile, newValue));
        chkTestCase.selectedProperty().addListener((observable, oldValue, newValue)  -> addOrRemoveRelationshipToPackage(RelationshipType.relationshipType_testcaseOf, newValue));
        chkDocumentation.selectedProperty().addListener((observable, oldValue, newValue)  -> addOrRemoveRelationshipToPackage(RelationshipType.relationshipType_documentation, newValue));
        chkOptionalComponent.selectedProperty().addListener((observable, oldValue, newValue)  -> addOrRemoveRelationshipToPackage(RelationshipType.relationshipType_optionalComponentOf, newValue));
        chkMetafile.selectedProperty().addListener((observable, oldValue, newValue)  -> addOrRemoveRelationshipToPackage(RelationshipType.relationshipType_metafileOf, newValue));
        chkBuildTool.selectedProperty().addListener((observable, oldValue, newValue) -> addOrRemoveRelationshipToPackage(RelationshipType.relationshipType_buildToolOf, newValue));
        //Initialize other elements

        tblColumnFile.setCellValueFactory(
                (TreeTableColumn.CellDataFeatures<SpdxFile, String> param) ->
                        new ReadOnlyStringWrapper(param.getValue().getValue().getName()));
        //Load all file types into the file type list in order.
        chkListFileTypes.getItems().setAll(Stream.of(FileType.values())
                .sorted(Ordering.usingToString()) //Sort
                .map(fileType -> StringableWrapper.wrap(fileType, SpdxLogic::toString)) //Wrap so that the nice toString function gets used by the checkbox
                .collect(Collectors.toList()));
        chkListFileTypes.getCheckModel().getCheckedItems().addListener(this::handleFileTypeCheckedOrUnchecked);
        filesTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> handleFileSelected(newValue));
        filesTable.setShowRoot(false);
    }


    public static void editPackage(final SpdxPackage pkg, Window parentWindow) {
        final PackageEditor packageEditor = new PackageEditor(pkg);
        final Stage dialogStage = new Stage();
        dialogStage.setTitle("Edit SPDX Package");
        dialogStage.initStyle(StageStyle.UTILITY);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setY(parentWindow.getX() + parentWindow.getWidth() / 2);
        dialogStage.setY(parentWindow.getY() + parentWindow.getHeight() / 2);
        dialogStage.setResizable(false);
        try {
            FXMLLoader loader = new FXMLLoader(PackagePropsSceneController.class.getResource("/PackageEditor.fxml"));
            loader.setController(packageEditor);
            Pane pane = loader.load();
            Scene scene = new Scene(pane);
            dialogStage.setScene(scene);
            //Populate the file list on appearance
            dialogStage.setOnShown(event ->
            {
                try {
                    final SpdxFile dummyfile = new SpdxFile(pkg.getName(), null, null, null, null, null, null, null, null, null, null, null, null);
                    TreeItem<SpdxFile> root = new TreeItem<>(dummyfile);
                    packageEditor.filesTable.setRoot(root);

                    root.getChildren().setAll(Stream.of(pkg.getFiles()).map(TreeItem<SpdxFile>::new).collect(Collectors.toList()));
                } catch (InvalidSPDXAnalysisException e) {
                    logger.error("Unable to get files for package " + pkg.getName(), e);
                }
            });

            //Won't assign this event through FXML - don't want to propagate the stage beyond this point.
            packageEditor.btnOk.setOnMouseClicked(event2 -> dialogStage.close());
            dialogStage.showAndWait();


        } catch (IOException ioe) {
            throw new RuntimeException("Unable to load dialog", ioe);
        }
    }

    private PackageEditor(SpdxPackage pkg) {
        this.pkg = pkg;

    }

    //Load the values for the file in all file editing contorls
    private void handleFileSelected(TreeItem<SpdxFile> newSelection) {
        if (newSelection == null) {
            currentFile = null;
        } else {
            //Set currentFile to null to make sure we don't accidentally edit the previous file
            currentFile = null;
            //Set the file type checkbox values to reflect this file's types
            chkListFileTypes.getCheckModel().clearChecks();
            //The element lookup by index seems to be broken on the CheckListView control,
            //so we'll have to provide the indices
            for (int i=0; i<chkListFileTypes.getItems().size(); ++i){
                StringableWrapper<FileType> item = chkListFileTypes.getItems().get(i);
                if (ArrayUtils.contains(newSelection.getValue().getFileTypes(), item.getValue())){
                    chkListFileTypes.getCheckModel().check(i);
                }
            }

            //Reset the relationship checkboxes
            chkDataFile.setSelected(SpdxLogic.findRelationship(newSelection.getValue(), RelationshipType.relationshipType_dataFile, pkg).isPresent());
            chkTestCase.setSelected(SpdxLogic.findRelationship(newSelection.getValue(), RelationshipType.relationshipType_testcaseOf, pkg).isPresent());
            chkDocumentation.setSelected(SpdxLogic.findRelationship(newSelection.getValue(), RelationshipType.relationshipType_documentation, pkg).isPresent());
            chkMetafile.setSelected(SpdxLogic.findRelationship(newSelection.getValue(), RelationshipType.relationshipType_metafileOf, pkg).isPresent());
            chkOptionalComponent.setSelected(SpdxLogic.findRelationship(newSelection.getValue(), RelationshipType.relationshipType_optionalComponentOf, pkg).isPresent());
            chkBuildTool.setSelected(SpdxLogic.findRelationship(newSelection.getValue(), RelationshipType.relationshipType_buildToolOf, pkg).isPresent());

            //Set the file relationship checkboxes
            Relationship[] relationships = newSelection.getValue().getRelationships();

            currentFile = newSelection.getValue();
        }
    }

    private void handleFileTypeCheckedOrUnchecked(ListChangeListener.Change<? extends StringableWrapper<FileType>> change){
        if (currentFile == null) return;
        FileType[] newFileTypes = change.getList().stream()
                .map(wrappedType -> wrappedType.getValue()) //Unwrap the stringable wrapper
                .toArray(size -> new FileType[size]); //Get array
        try {
            currentFile.setFileTypes(newFileTypes);
        } catch (InvalidSPDXAnalysisException e){
            logger.error("Unable to update types of file "+currentFile.getName());
        }
    }

    private void addOrRemoveRelationshipToPackage(RelationshipType relationshipType, boolean shouldExist){
        if (currentFile != null) {
            SpdxLogic.setFileRelationshipToPackage(currentFile, pkg, relationshipType, shouldExist);
        }
    }



}