'use client';

// material-ui
import { Grid  } from '@mui/material';

// project imports
import AlertDialog from 'components/ui-elements/advance/UIDialog/AlertDialog';
import AlertDialogSlide from 'components/ui-elements/advance/UIDialog/AlertDialogSlide';
import ConfirmationDialog from 'components/ui-elements/advance/UIDialog/ConfirmationDialog';
import CustomizedDialogs from 'components/ui-elements/advance/UIDialog/CustomizedDialogs';
import DraggableDialog from 'components/ui-elements/advance/UIDialog/DraggableDialog';
import FormDialog from 'components/ui-elements/advance/UIDialog/FormDialog';
import FullScreenDialog from 'components/ui-elements/advance/UIDialog/FullScreenDialog';
import MaxWidthDialog from 'components/ui-elements/advance/UIDialog/MaxWidthDialog';
import ResponsiveDialog from 'components/ui-elements/advance/UIDialog/ResponsiveDialog';
import ScrollDialog from 'components/ui-elements/advance/UIDialog/ScrollDialog';
import SimpleDialogDemo from 'components/ui-elements/advance/UIDialog/SimpleDialog';
import { gridSpacing } from 'constants/index';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';
import SubCard from 'ui-component/cards/SubCard';

// ===============================|| UI DIALOG ||=============================== //

const UIDialog = () => (
  <MainCard>
    <MainCard.Header title="Dialog" action={<SecondaryAction link="https://next.material-ui.com/components/dialogs/" />} />
    <Grid container spacing={gridSpacing}>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Simple Dialog">
          <Grid container justifyContent="center">
            <SimpleDialogDemo />
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Sweet Alert">
          <Grid container justifyContent="center">
            <AlertDialog />
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Dialog Animation">
          <Grid container justifyContent="center">
            <AlertDialogSlide />
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Form Dialogs">
          <Grid container justifyContent="center">
            <FormDialog />
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Customized Dialogs">
          <Grid container justifyContent="center">
            <CustomizedDialogs />
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Full Screen Dialogs">
          <Grid container justifyContent="center">
            <FullScreenDialog />
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Dialogs Size">
          <Grid container justifyContent="center">
            <MaxWidthDialog />
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Responsive Fullscreen Dialogs">
          <Grid container justifyContent="center">
            <ResponsiveDialog />
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Draggable Dialogs">
          <Grid container justifyContent="center">
            <DraggableDialog />
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Scrolling Dialogs">
          <Grid container justifyContent="center">
            <ScrollDialog />
          </Grid>
        </SubCard>
      </Grid>
      <Grid size={{ xs: 12, sm: 6 }}>
        <SubCard title="Confirmation Dialogs">
          <Grid container justifyContent="center">
            <ConfirmationDialog />
          </Grid>
        </SubCard>
      </Grid>
    </Grid>
  </MainCard>
);

export default UIDialog;
