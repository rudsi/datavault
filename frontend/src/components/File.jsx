import { ImageIcon, FolderOpen } from "lucide-react";
import { SlOptionsVertical } from "react-icons/sl";
import FileOptions from "./UI/FileOptions";

const File = () => {
  return (
    <div className="w-full flex justify-between items-center px-4">
      <div className="bg-blue-400 rounded-full p-2 flex items-center justify-center shadow-md w-fit">
        <FolderOpen className="text-white w-6 h-6" />
      </div>
      <div className="w-[70%]">
        <h1>File Name.Docs</h1>
        <p>4:57PM, 25 SEP</p>
      </div>
      <div className="w-fit cursor-pointer"><FileOptions/></div>
    </div>
  );
};

export default File;
