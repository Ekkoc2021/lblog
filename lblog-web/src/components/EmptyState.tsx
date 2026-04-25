import { Empty, Button } from 'antd';
import type { ReactNode } from 'react';

interface EmptyStateProps {
  icon?: ReactNode;
  description: ReactNode;
  actionText?: string;
  onAction?: () => void;
}

const EmptyState: React.FC<EmptyStateProps> = ({ icon, description, actionText, onAction }) => (
  <Empty
    image={icon || <div />}
    description={description}
  >
    {actionText && onAction && (
      <Button type="primary" onClick={onAction}>{actionText}</Button>
    )}
  </Empty>
);

export default EmptyState;
