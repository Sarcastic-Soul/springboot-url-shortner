import { createContext, useState, useContext, ReactNode, useEffect } from 'react';
import { MantineProvider, createTheme } from '@mantine/core';

interface ThemeContextType {
  primaryColor: string;
  setPrimaryColor: (color: string) => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export function AppThemeProvider({ children }: { children: ReactNode }) {
  const [primaryColor, setPrimaryColor] = useState<string>(localStorage.getItem('primaryColor') || 'blue');

  useEffect(() => {
    localStorage.setItem('primaryColor', primaryColor);
  }, [primaryColor]);

  const theme = createTheme({
    primaryColor,
    other: {
      softBgLight: 'var(--mantine-color-gray-0)',
      softBgDark: 'var(--mantine-color-dark-8)',
    }
  });

  return (
    <ThemeContext.Provider value={{ primaryColor, setPrimaryColor }}>
      <MantineProvider defaultColorScheme="auto" theme={theme}>
        {children}
      </MantineProvider>
    </ThemeContext.Provider>
  );
}

export function useAppTheme() {
  const context = useContext(ThemeContext);
  if (context === undefined) {
    throw new Error('useAppTheme must be used within an AppThemeProvider');
  }
  return context;
}
