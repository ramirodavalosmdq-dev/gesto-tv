export const FilesetResolver={forVisionTasks:async()=>({})};
export const HandLandmarker={createFromOptions:async()=>({detectForVideo:()=>({landmarks: window.__hand? [window.__hand]:[]})})};
