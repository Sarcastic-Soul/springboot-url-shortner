import React, { type ReactNode, useEffect } from 'react';
import { IconX } from '@tabler/icons-react';

export const Modal = ({ 
  isOpen, 
  onOpenChange,
  children 
}: { 
  isOpen: boolean; 
  onOpenChange: (open: boolean) => void;
  children: ReactNode;
}) => {
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = 'unset';
    }
    return () => {
      document.body.style.overflow = 'unset';
    };
  }, [isOpen]);

  if (!isOpen) return null;

  let content: ReactNode = null;
  React.Children.forEach(children, child => {
    if (React.isValidElement(child) && (child.type as any).displayName === 'ModalContent') {
      content = child;
    }
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div 
        className="fixed inset-0 bg-black/40 backdrop-blur-sm transition-opacity" 
        onClick={() => onOpenChange(false)}
      />
      
      {/* Modal Dialog */}
      <div className="relative bg-white dark:bg-slate-900 rounded-xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-hidden flex flex-col transform transition-all border border-gray-200 dark:border-slate-800">
        {content}
      </div>
    </div>
  );
};

Modal.Content = ({ children, className = '' }: { children: ReactNode | ((onClose: () => void) => ReactNode), className?: string }) => {
  return (
    <div className={`flex flex-col w-full h-full ${className}`}>
      {typeof children === 'function' ? children(() => {}) : children}
    </div>
  );
};
(Modal.Content as any).displayName = 'ModalContent';

export const ModalHeader = ({ children, className = '', onClose }: { children: ReactNode, className?: string, onClose?: () => void }) => (
  <div className={`px-6 py-4 border-b border-gray-100 dark:border-slate-800 flex justify-between items-center ${className}`}>
    <div className="text-lg font-semibold">{children}</div>
    {onClose && (
      <button 
        onClick={onClose}
        className="p-1 rounded-md hover:bg-gray-100 dark:hover:bg-slate-800 text-gray-500 dark:text-slate-400 transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500"
      >
        <IconX size={20} />
      </button>
    )}
  </div>
);

export const ModalBody = ({ children, className = '' }: { children: ReactNode, className?: string }) => (
  <div className={`p-6 overflow-y-auto ${className}`}>
    {children}
  </div>
);

export const ModalFooter = ({ children, className = '' }: { children: ReactNode, className?: string }) => (
  <div className={`px-6 py-4 border-t border-gray-100 dark:border-slate-800 flex justify-end gap-2 bg-gray-50/50 dark:bg-slate-900/50 ${className}`}>
    {children}
  </div>
);
