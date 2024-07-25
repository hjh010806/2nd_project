import dynamic from 'next/dynamic';
import React, { useState } from 'react';
import { DateValueType } from 'react-tailwindcss-datepicker/dist/types';

// 동적 import
const DatePicker = dynamic(() => import('react-tailwindcss-datepicker'), { ssr: false });

interface DatePickerProps {
    onDateChange: (newValue: DateValueType) => void;
}

const DatePickerComponent: React.FC<DatePickerProps> = ({ onDateChange }) => {
    const [value, setValue] = useState<DateValueType>({
        startDate: null,
        endDate: null,
    });

    const handleValueChange = (newValue: DateValueType) => {
        setValue(newValue);
        onDateChange(newValue); // Call the prop function to notify the parent
    };

    const configs = {
        shortcuts: {
            today: '오늘',
            yesterday: '어제',
            past: (period: number) => `${period}일 전`,
            currentMonth: '이번 달',
            pastMonth: '지난 달'
        },
        footer: {
            cancel: '취소',
            apply: '적용'
        }
    };

    return (
        <div className="dark">
            <DatePicker
                value={value}
                onChange={handleValueChange}
                showShortcuts={true}
                primaryColor={'orange'}
                configs={configs}
                i18n='ko'
            />
        </div>
    );
};


export default DatePickerComponent;