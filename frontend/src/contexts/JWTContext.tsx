'use client';

import { Chance } from 'chance';
import { jwtDecode } from 'jwt-decode';
import React, { createContext, useEffect, useState } from 'react';

// third-party

// Using Context API
// accountReducer and actions replaced with modern Context implementation

// project imports
import { InitialLoginContextProps } from 'types';
import Loader from 'ui-component/Loader';
import axios from 'utils/axios';

// types

const chance = new Chance();

// constant
const initialState: InitialLoginContextProps = {
  isLoggedIn: false,
  isInitialized: false,
  user: null,
};

type JWTDecodedToken = { exp: number };

const verifyToken: (st: string) => boolean = serviceToken => {
  if (!serviceToken) {
    return false;
  }
  const decoded = jwtDecode<JWTDecodedToken>(serviceToken);
  return decoded.exp > Date.now() / 1000;
};

const setSession = (serviceToken?: string | null) => {
  if (serviceToken) {
    localStorage.setItem('serviceToken', serviceToken);
    axios.defaults.headers.common.Authorization = `Bearer ${serviceToken}`;
  } else {
    localStorage.removeItem('serviceToken');
    delete axios.defaults.headers.common.Authorization;
  }
};

// ==============================|| JWT CONTEXT & PROVIDER ||============================== //
type JWTContextType = InitialLoginContextProps & {
  login: (email: string, password: string) => Promise<void>;
  register: (
    email: string,
    password: string,
    firstName: string,
    lastName: string
  ) => Promise<void>;
  logout: () => void;
  resetPassword: (email: string) => Promise<void> | void;
  updateProfile: () => void;
};

const JWTContext = createContext<JWTContextType | null>(null);

export const JWTProvider = ({ children }: { children: React.ReactElement }) => {
  const [state, setState] = useState(initialState);

  useEffect(() => {
    const init = async () => {
      try {
        const serviceToken = window.localStorage.getItem('serviceToken');
        if (serviceToken && verifyToken(serviceToken)) {
          setSession(serviceToken);
          try {
            const response = await axios.get('/api/account/me');
            const { user } = response.data;
            setState(prev => ({
              ...prev,
              isInitialized: true,
              isLoggedIn: true,
              user,
            }));
          } catch (apiError) {
            // If API call fails, clear the token and set as not logged in
            console.warn('API call failed, clearing token:', apiError);
            setSession(null);
            setState(prev => ({
              ...prev,
              isInitialized: true,
              isLoggedIn: false,
              user: null,
            }));
          }
        } else {
          setState(prev => ({
            ...prev,
            isInitialized: true,
            isLoggedIn: false,
            user: null,
          }));
        }
      } catch (err) {
        console.error('JWT Context initialization error:', err);
        setState(prev => ({
          ...prev,
          isInitialized: true,
          isLoggedIn: false,
          user: null,
        }));
      }
    };

    init();
  }, []);

  const login = async (email: string, password: string) => {
    const response = await axios.post('/api/account/login', { email, password });
    const { serviceToken, user } = response.data;
    setSession(serviceToken);
    setState({
      ...state,
      isLoggedIn: true,
      user,
    });
  };

  const register = async (email: string, password: string, firstName: string, lastName: string) => {
    // todo: this flow need to be recode as it not verified
    const id = chance.bb_pin();
    const response = await axios.post('/api/account/register', {
      id,
      email,
      password,
      firstName,
      lastName,
    });
    let users = response.data;

    if (
      window.localStorage.getItem('users') !== undefined &&
      window.localStorage.getItem('users') !== null
    ) {
      const localUsers = window.localStorage.getItem('users');
      users = [
        ...JSON.parse(localUsers!),
        {
          id,
          email,
          password,
          name: `${firstName} ${lastName}`,
        },
      ];
    }

    window.localStorage.setItem('users', JSON.stringify(users));
  };

  const logout = () => {
    setSession(null);
    setState({
      ...state,
      isLoggedIn: false,
      user: null,
    });
  };

  const resetPassword = async (_email: string) => {};

  const updateProfile = () => {};

  if (state.isInitialized !== undefined && !state.isInitialized) {
    return <Loader />;
  }

  return (
    <JWTContext.Provider
      value={{ ...state, login, logout, register, resetPassword, updateProfile }}
    >
      {children}
    </JWTContext.Provider>
  );
};

export default JWTContext;
