import React, { type ButtonHTMLAttributes } from 'react';

export const Spinner = ({ size = 'md', className = '' }: { size?: 'sm' | 'md' | 'lg', className?: string }) => {
  const sizeClasses = {
    sm: 'w-4 h-4 border-2',
    md: 'w-6 h-6 border-2',
    lg: 'w-8 h-8 border-3',
  };
  return (
    <div 
      className={`inline-block rounded-full animate-spin border-solid border-t-transparent border-current ${sizeClasses[size]} ${className}`} 
      role="status"
    />
  );
};

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'ghost' | 'outline' | 'danger' | 'danger-soft';
  size?: 'sm' | 'md' | 'lg';
  isIconOnly?: boolean;
  onPress?: (e: any) => void;
  isDisabled?: boolean;
}

export const Button = ({ 
  className = '', 
  variant = 'primary', 
  size = 'md',
  isIconOnly = false,
  onPress,
  onClick,
  isDisabled,
  disabled,
  children,
  ...props 
}: ButtonProps) => {
  const baseClasses = "inline-flex items-center justify-center font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2";
  
  const variantClasses = {
    primary: "bg-blue-600 text-white hover:bg-blue-700 active:bg-blue-800",
    ghost: "bg-transparent hover:bg-gray-100 dark:hover:bg-slate-800 text-gray-700 dark:text-slate-300 active:bg-gray-200 dark:active:bg-slate-700",
    outline: "border border-gray-300 dark:border-slate-700 bg-transparent hover:bg-gray-50 dark:hover:bg-slate-800 text-gray-700 dark:text-slate-300",
    danger: "bg-red-600 text-white hover:bg-red-700 active:bg-red-800",
    "danger-soft": "bg-red-50 dark:bg-red-950/30 text-red-600 dark:text-red-400 hover:bg-red-100 dark:hover:bg-red-900/50 active:bg-red-200 dark:active:bg-red-800/50"
  };

  const sizeClasses = isIconOnly 
    ? (size === 'sm' ? "p-1.5 rounded-md" : "p-2 rounded-lg")
    : (size === 'sm' ? "px-3 py-1.5 text-sm rounded-md" : "px-4 py-2 rounded-lg");

  const disabledClasses = isDisabled || disabled ? "opacity-50 cursor-not-allowed pointer-events-none" : "cursor-pointer";

  const handleClick = (e: React.MouseEvent<HTMLButtonElement>) => {
    if (isDisabled || disabled) return;
    if (onPress) onPress(e);
    if (onClick) onClick(e);
  };

  return (
    <button 
      className={`${baseClasses} ${variantClasses[variant]} ${sizeClasses} ${disabledClasses} ${className}`}
      onClick={handleClick}
      disabled={isDisabled || disabled}
      {...props}
    >
      {children}
    </button>
  );
};
