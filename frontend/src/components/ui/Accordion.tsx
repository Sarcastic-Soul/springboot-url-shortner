import React, { type ReactNode, useState } from 'react';
import { IconChevronDown } from '@tabler/icons-react';

export const Accordion = ({ children, className = '' }: { children: ReactNode, className?: string, variant?: string }) => {
  return (
    <div className={`flex flex-col gap-2 ${className}`}>
      {children}
    </div>
  );
};

Accordion.Item = ({ children, className = '', 'aria-label': ariaLabel }: { children: ReactNode, className?: string, id?: string, 'aria-label'?: string }) => {
  const [isOpen, setIsOpen] = useState(false);
  
  let trigger: ReactNode = null;
  let panel: ReactNode = null;

  React.Children.forEach(children, child => {
    if (React.isValidElement(child)) {
      if ((child.type as any).displayName === 'AccordionTrigger') {
        trigger = (child.props as any).children;
      } else if ((child.type as any).displayName === 'AccordionPanel') {
        panel = child;
      }
    }
  });

  return (
    <div className={`border border-gray-200 dark:border-slate-800 rounded-lg overflow-hidden bg-white dark:bg-slate-900 ${className}`}>
      <button 
        type="button"
        className="w-full px-4 py-3 flex justify-between items-center hover:bg-gray-50 dark:hover:bg-slate-800/50 transition-colors focus:outline-none"
        onClick={() => setIsOpen(!isOpen)}
        aria-expanded={isOpen}
        aria-label={ariaLabel}
      >
        {trigger}
        <IconChevronDown 
          size={18} 
          className={`text-gray-500 dark:text-slate-400 transition-transform duration-200 ${isOpen ? 'rotate-180' : ''}`} 
        />
      </button>
      {isOpen && (
        <div className="px-4 pb-3 pt-1 border-t border-gray-100 dark:border-slate-800">
          {panel}
        </div>
      )}
    </div>
  );
};

Accordion.Trigger = ({ children, className = '' }: { children: ReactNode, className?: string }) => {
  return <div className={className}>{children}</div>;
};
(Accordion.Trigger as any).displayName = 'AccordionTrigger';

Accordion.Panel = ({ children, className = '' }: { children: ReactNode, className?: string }) => {
  return <div className={className}>{children}</div>;
};
(Accordion.Panel as any).displayName = 'AccordionPanel';
