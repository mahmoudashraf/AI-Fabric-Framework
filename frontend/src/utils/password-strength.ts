/**
 * Password validator for login pages
 */
import value from '../scss/_themes-vars.module.scss';
import { NumbColorFunc, StringBoolFunc, StringNumFunc } from '../types';

// has number
const hasNumber: StringBoolFunc = number => new RegExp(/[0-9]/).test(number);

// has mix of small and capitals
const hasMixed: StringBoolFunc = number =>
  new RegExp(/[a-z]/).test(number) && new RegExp(/[A-Z]/).test(number);

// has special chars
const hasSpecial: StringBoolFunc = number => new RegExp(/[!#@$%^&*)(+=._-]/).test(number);

// set color based on password strength
export const strengthColor: NumbColorFunc = count => {
  if (count < 2) return { label: 'Poor', color: value.errorMain || '#f44336' };
  if (count < 3) return { label: 'Weak', color: value.warningDark || '#ffa000' };
  if (count < 4) return { label: 'Normal', color: value.orangeMain || '#ff9800' };
  if (count < 5) return { label: 'Good', color: value.successMain || '#4caf50' };
  if (count < 6) return { label: 'Strong', color: value.successDark || '#2e7d32' };
  return { label: 'Poor', color: value.errorMain || '#f44336' };
};

// password strength indicator
export const strengthIndicator: StringNumFunc = number => {
  let strengths = 0;
  if (number.length > 5) strengths += 1;
  if (number.length > 7) strengths += 1;
  if (hasNumber(number)) strengths += 1;
  if (hasSpecial(number)) strengths += 1;
  if (hasMixed(number)) strengths += 1;
  return strengths;
};
