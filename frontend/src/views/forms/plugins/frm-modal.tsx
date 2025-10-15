'use client';

// project imports
import ServerModal from 'components/forms/plugins/Modal/ServerModal';
import SimpleModal from 'components/forms/plugins/Modal/SimpleModal';
import SecondaryAction from 'ui-component/cards/CardSecondaryAction';
import MainCard from 'ui-component/cards/MainCard';

// ==============================|| MODAL PAGE ||============================== //

const Modal = () => (
  <MainCard
    title="Simple Modal"
    secondary={<SecondaryAction link="https://next.material-ui.com/components/modal/" />}
  >
    <ServerModal />
    <SimpleModal />
  </MainCard>
);

export default Modal;
