import React, { type InputHTMLAttributes, type TextareaHTMLAttributes, type LabelHTMLAttributes, type ReactNode } from 'react';

export const Label = ({ className = '', ...props }: LabelHTMLAttributes<HTMLLabelElement>) => (
  <label className={`text-sm font-medium text-gray-700 dark:text-slate-300 ${className}`} {...props} />
);

export const Input = React.forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  ({ className = '', ...props }, ref) => (
    <input
      ref={ref}
      className={`w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-shadow ${className}`}
      {...props}
    />
  )
);
Input.displayName = 'Input';

export const TextArea = React.forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className = '', ...props }, ref) => (
    <textarea
      ref={ref}
      className={`w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-shadow ${className}`}
      {...props}
    />
  )
);
TextArea.displayName = 'TextArea';

export const TextField = ({ children, className = '', value, onChange, disabled, isDisabled, isRequired, required }: { children: ReactNode, className?: string, value?: string, onChange?: (val: string) => void, disabled?: boolean, isDisabled?: boolean, isRequired?: boolean, required?: boolean }) => {
  const childrenWithProps = React.Children.map(children, child => {
    if (React.isValidElement(child) && (child.type === Input || child.type === TextArea)) {
      return React.cloneElement(child as any, { 
        value, 
        disabled: disabled || isDisabled,
        required: required || isRequired,
        onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => onChange?.(e.target.value) 
      });
    }
    return child;
  });

  return (
    <div className={`flex flex-col gap-1.5 ${className}`}>
      {childrenWithProps}
    </div>
  );
};
