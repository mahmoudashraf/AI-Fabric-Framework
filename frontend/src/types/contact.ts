// types
import { UserProfile } from 'types/user-profile';

// ==============================|| TYPES - CONTACT ||============================== //

export interface ContactStateProps {
  contacts: UserProfile[];
  error: object | string | null;
}
